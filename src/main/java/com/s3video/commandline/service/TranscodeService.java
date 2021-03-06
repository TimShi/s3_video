package com.s3video.commandline.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudfront.model.Distribution;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.s3video.commandline.adapter.AWSAdapter;
import com.s3video.commandline.model.JobStatusNotification;
import com.s3video.commandline.model.Transcoder;
import com.s3video.commandline.properties.PropertiesManager;
import com.s3video.commandline.repository.TranscoderRepository;

public class TranscodeService {
	final static Logger logger = LoggerFactory.getLogger(TranscodeService.class);

	private static Pattern SOURCE_FILE_NAME_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)\\.[a-zA-Z0-9]+"); 
	private static String DIRECTORY_FOR_TRANSCODED_VIDEOS = "transcoded";
	private static String DIRECTORY_FOR_GIF_VIDEOS = "transcoded_gif";
	
	public TranscodeService() throws IOException {		
		super();
		
		PropertiesManager propertiesManager = PropertiesManager.getInstance();
		
		inputBucketName = propertiesManager.getPropertyByName("aws.s3.bucket.in.name");
		inputBucketRegion = propertiesManager.getPropertyByName("aws.s3.bucket.in.region");
		outputBucketName = propertiesManager.getPropertyByName("aws.s3.bucket.out.name");
		outputBucketRegion = propertiesManager.getPropertyByName("aws.s3.bucket.out.region");
		storageClass = propertiesManager.getPropertyByName("aws.elastictranscoder.pipeline.storageclass");
		pipelineName = propertiesManager.getPropertyByName("aws.elastictranscoder.pipeline.name");
		regionName = propertiesManager.getPropertyByName("aws.elastictranscoder.pipeline.region");
		roleName = propertiesManager.getPropertyByName("aws.iam.role.name");
		notificationTopic = propertiesManager.getPropertyByName("aws.notification.topic");
		cloudWatchNotificationTopic = propertiesManager.getPropertyByName("aws.cloudwatch.notification.topic");
		transcoderProgressQueueName = propertiesManager.getPropertyByName("aws.sqs.queue.name");
		billingAlarmEmailAddress = propertiesManager.getPropertyByName("aws.cloudwatch.billing.alarm.email");		
	}
		
	private String inputBucketName;

	private String inputBucketRegion;

	private String outputBucketName;

	private String outputBucketRegion;

	private String storageClass;

	private String pipelineName;

	private String regionName;

	private String roleName;

	private String notificationTopic;
	
	private String cloudWatchNotificationTopic;
	
	private String transcoderProgressQueueName;
	
	private String billingAlarmEmailAddress;

	private AWSAdapter awsAdapter;	
	
	private TranscoderRepository transcoderRepository;
	
	public AWSAdapter getAwsAdapter() {
		return awsAdapter;
	}

	public void setAwsAdapter(AWSAdapter awsAdapter) {
		this.awsAdapter = awsAdapter;
	}

	public TranscoderRepository getTranscoderRepository() {
		return transcoderRepository;
	}

	public void setTranscoderRepository(TranscoderRepository transcoderRepository) {
		this.transcoderRepository = transcoderRepository;
	}
	
	/*
	 * Configuration
	 */
	
	public void configure(boolean forceConfig) throws TranscodeException, SettingsExistException, IOException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized()) || forceConfig){
			awsAdapter.createS3BucketIfNotExist(inputBucketName, inputBucketRegion, false);
			awsAdapter.createS3BucketIfNotExist(outputBucketName, outputBucketRegion, true);
			String roleArn = awsAdapter.createIAMRoleIfNotExist(roleName);
			String topicArn = awsAdapter.createNotificationTopicIfNotExist(notificationTopic);
			String pipelineId = awsAdapter.createPipeLineIfNotExist(pipelineName, regionName, inputBucketName, outputBucketName, roleArn, topicArn, storageClass);			
			String billingTopicArn = awsAdapter.createNotificationTopicIfNotExist(cloudWatchNotificationTopic);
			awsAdapter.createAlarmIfNotAlreadyExist(billingTopicArn);

			String queueUrl = awsAdapter.createMessageQueue(transcoderProgressQueueName);
			String sqsSubscriptionArn = awsAdapter.subscribeQueueToTopic(topicArn, queueUrl);
					
			String billingSubscriptionArn = awsAdapter.subscribeEmailToTopic(billingTopicArn, billingAlarmEmailAddress);
			
			Distribution distribution = awsAdapter.createCDNIfNotExist();
			transcoder.setPipelineId(pipelineId);
			transcoder.setDistributionDomainName(distribution.getDomainName());
			transcoder.setDistributionId(distribution.getId());
			transcoder.setNotificationTopicArn(topicArn);
			transcoder.setBillingNotificationTopicArn(billingTopicArn);
			transcoder.setNotificationSubscriptionArn(sqsSubscriptionArn);
			transcoder.setBillingNotificationSubscriptionArn(billingSubscriptionArn);
			transcoder.setNotificationQueueUrl(queueUrl);
			transcoder.setIsInitialized("true");			
			transcoderRepository.save(transcoder);			
		} else {
			throw new SettingsExistException("Transcoder already initialized");
		}
	}

	/*
	 * Push
	 * Note: If video with same name already exist, the upload will succeed but the transcode won't
	 */
	public List<String> push(String sourceFilePath) throws TranscodeException, IOException, InvalidNameException{
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		
		File inputFile = new File(sourceFilePath);
		File[] assetFiles = getAssetFiles(inputFile);

		File transcodedDirectory = null;
		if (inputFile.isDirectory()) {
			transcodedDirectory = makeDirectoryForProcessedFilesIfNotExist(inputFile, DIRECTORY_FOR_TRANSCODED_VIDEOS); 
		} 

		List<String> keys = new ArrayList<>();
		
		DateTime date = new DateTime();
		for (int i = 0; i < assetFiles.length; i++) {
			if (!assetFiles[i].isDirectory() && !assetFiles[i].isHidden()) {
				String key = uploadToS3(assetFiles[i]);
				logger.info("Upload to s3 complete, transcode started. This may take a while");
				CreateJobResult result = awsAdapter.createTranscodeJob(transcoder.getPipelineId(), key);
				String jobId = result.getJob().getId();
				waitForJobCompletion(transcoder.getNotificationQueueUrl(), jobId);	
				keys.add(key);
				
				if (transcodedDirectory != null) {
					assetFiles[i].renameTo(new File(transcodedDirectory, assetFiles[i].getName() + "_" + date.getMillis()));
				}
			}
		}
		
		return keys;
	}
	
	public void pushGif(String sourceFilePath) throws TranscodeException, InvalidNameException, IOException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		
		File inputFile = new File(sourceFilePath);
		File[] assetFiles = getAssetFiles(inputFile);		
		File transcodedDirectory = null;
		if (inputFile.isDirectory()) {
			transcodedDirectory = makeDirectoryForProcessedFilesIfNotExist(inputFile, DIRECTORY_FOR_GIF_VIDEOS);
		}
		
		DateTime date = new DateTime();
		for (int i = 0; i < assetFiles.length; i++) {
			if (!assetFiles[i].isDirectory() && !assetFiles[i].isHidden()) {
				String key = uploadToS3(assetFiles[i]);
				logger.info("Upload to s3 complete, transcode started. This may take a while");
				CreateJobResult result = awsAdapter.createGifJob(transcoder.getPipelineId(), key);
				String jobId = result.getJob().getId();
				waitForJobCompletion(transcoder.getNotificationQueueUrl(), jobId);
				
				if (transcodedDirectory != null) {
					assetFiles[i].renameTo(new File(transcodedDirectory, assetFiles[i].getName() + "_" + date.getMillis()));
				}
			}
		}
	}
	
	private File makeDirectoryForProcessedFilesIfNotExist(File parentDirectory, String directoryName) throws TranscodeException {
		File transcodedDirectory = new File(parentDirectory, directoryName);
		if (!transcodedDirectory.exists()) {
			transcodedDirectory.mkdir();
		} else {
			if (!transcodedDirectory.isDirectory()) {
				throw new TranscodeException("Expect directory called transcoded_gifs.");
			}
		}
		return transcodedDirectory;
	}
	
	private File[] getAssetFiles(File inputFile) {
		File[] assetFiles;
		if (inputFile.isDirectory()) {
			assetFiles = inputFile.listFiles();
		} else {
			assetFiles = new File[1];
			assetFiles[0] = inputFile;
		}
		return assetFiles;
	}
	
	private String uploadToS3(File assetFile) throws TranscodeException, InvalidNameException {		
		if (!assetFile.exists() || !assetFile.isFile()) {
			throw new TranscodeException("Source file does not exist");
		}
		
		String key = generateAssetKeyByName(assetFile.getName());
		
		if (!awsAdapter.listKeysInInputBucket(inputBucketName).contains(key)){
			awsAdapter.uploadAssetToS3Bucket(inputBucketName, key, assetFile, storageClass);				
		}
		return key;
	}
	
	private void waitForJobCompletion(String notificationQueueUrl, String jobId) {
		boolean done = false;
		
		while (!done) {
			List<JobStatusNotification> jobStatusNotifications = awsAdapter.pollMessageFromQueueByJobId(notificationQueueUrl, jobId);
			
			for (JobStatusNotification jobStatus : jobStatusNotifications) {
				logger.info("Transcode job {} is {}", jobStatus.getJobId(), jobStatus.getState());
				if (jobStatus.getState().isTerminalState()){					
					done = true;
					
					if (jobStatus.getState() != JobStatusNotification.JobState.COMPLETED) {
						logger.info(jobStatus.toString());
					}
				}
			}
		}	
	}
	
	/*
	 * List
	 */

	public void listSourceVideos() throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		for (String key : awsAdapter.listKeysInInputBucket(inputBucketName)) {
			logger.info(key);
		}
	}
	
	public void listTranscodedVideos() throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		for (String key : awsAdapter.listKeysInOutputBucket(outputBucketName)) {
			logger.info(key);
		}		
	}
	
	public void listSettings() throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		logger.info("Configuration initialized: " + transcoder.getIsInitialized());
		logger.info("CDN domain is: " + transcoder.getDistributionDomainName());			
	}
	
	/*
	 * Delete
	 */	
	
	public void deleteAll(String key) throws IOException, TranscodeException{
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		awsAdapter.deleteAsset(inputBucketName, key);
		awsAdapter.deleteTranscodedAsset(outputBucketName, key);
	}
	
	public void deleteSource(String key) throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		awsAdapter.deleteAsset(inputBucketName, key);		
	}

	public void deleteTranscoded(String key) throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		awsAdapter.deleteTranscodedAsset(outputBucketName, key);		
	}
	
	/*
	 * Stop
	 */
	public void stop() throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();
		if (!"true".equalsIgnoreCase(transcoder.getIsInitialized())){
			throw new TranscodeException("Transcoder not initialized");
		}
		
		awsAdapter.pausePipeLine(transcoder.getPipelineId());
		logger.info("transcode pipeline is paused.");
		
		awsAdapter.disableCDN(transcoder.getDistributionId());
		logger.info("cloud front distribution {} is disabled.", transcoder.getDistributionDomainName());
		
		awsAdapter.removePublicAccessToBucket(outputBucketName);
		logger.info("s3 bucket {} is made private.", outputBucketName);
	}
	
	private String generateAssetKeyByName(String name) throws InvalidNameException{
		Matcher matcher = SOURCE_FILE_NAME_PATTERN.matcher(name);
		
		if (matcher.matches()) {
			String key = matcher.group(1);
			return key;
		} else {
			throw new InvalidNameException("Only accepts file name with alphanumeric characters.");
		}
	}
}