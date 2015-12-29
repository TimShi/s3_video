package com.s3video.commandline.adapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.Statement.Effect;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.auth.policy.conditions.ConditionFactory;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClient;
import com.amazonaws.services.cloudfront.model.Aliases;
import com.amazonaws.services.cloudfront.model.AllowedMethods;
import com.amazonaws.services.cloudfront.model.CacheBehaviors;
import com.amazonaws.services.cloudfront.model.CookiePreference;
import com.amazonaws.services.cloudfront.model.CreateDistributionRequest;
import com.amazonaws.services.cloudfront.model.CreateDistributionResult;
import com.amazonaws.services.cloudfront.model.DefaultCacheBehavior;
import com.amazonaws.services.cloudfront.model.Distribution;
import com.amazonaws.services.cloudfront.model.DistributionConfig;
import com.amazonaws.services.cloudfront.model.DistributionSummary;
import com.amazonaws.services.cloudfront.model.ForwardedValues;
import com.amazonaws.services.cloudfront.model.GetDistributionRequest;
import com.amazonaws.services.cloudfront.model.GetDistributionResult;
import com.amazonaws.services.cloudfront.model.ListDistributionsRequest;
import com.amazonaws.services.cloudfront.model.ListDistributionsResult;
import com.amazonaws.services.cloudfront.model.LoggingConfig;
import com.amazonaws.services.cloudfront.model.Method;
import com.amazonaws.services.cloudfront.model.Origin;
import com.amazonaws.services.cloudfront.model.Origins;
import com.amazonaws.services.cloudfront.model.S3OriginConfig;
import com.amazonaws.services.cloudfront.model.TrustedSigners;
import com.amazonaws.services.cloudfront.model.UpdateDistributionRequest;
import com.amazonaws.services.cloudfront.model.ViewerProtocolPolicy;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobPlaylist;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.CreatePipelineRequest;
import com.amazonaws.services.elastictranscoder.model.CreatePipelineResult;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.elastictranscoder.model.ListPipelinesResult;
import com.amazonaws.services.elastictranscoder.model.Notifications;
import com.amazonaws.services.elastictranscoder.model.Pipeline;
import com.amazonaws.services.elastictranscoder.model.PipelineOutputConfig;
import com.amazonaws.services.elastictranscoder.model.UpdatePipelineStatusRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.SetBucketPolicyRequest;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s3video.commandline.model.JobStatusNotification;
import com.s3video.commandline.model.Notification;
import com.s3video.commandline.properties.PropertiesManager;
import com.s3video.commandline.service.TranscodeException;

public class AWSAdapter {
	final static Logger logger = LoggerFactory.getLogger(AWSAdapter.class);
		
	private static int SIX_HOUR_IN_SECONDS = 6*3600;
    private static final int MAX_NUMBER_OF_MESSAGES = 5;
    private static final int VISIBILITY_TIMEOUT = 15;
    private static final int WAIT_TIME_SECONDS = 15;    
	private static final ObjectMapper mapper = new ObjectMapper();

	@SuppressWarnings("unchecked")
	public AWSAdapter() throws IOException {
		super();
				
		originId = PropertiesManager.getInstance().getPropertyByName("aws.cloudfront.origin.id");
		
		s3Domain = PropertiesManager.getInstance().getPropertyByName("aws.s3.domain");
		
		cloudfrontPriceClass = PropertiesManager.getInstance().getPropertyByName("aws.cloudfront.price");
		
		cloudfrontCallReference = PropertiesManager.getInstance().getPropertyByName("aws.cloudfront.callreference");
		
		cloudfrontMinttl = Long.parseLong(PropertiesManager.getInstance().getPropertyByName("aws.cloudfront.ttl.min"));

		segmentDuration = PropertiesManager.getInstance().getPropertyByName("aws.elastictranscoder.segmentduration");

		outputKeyPrefix = PropertiesManager.getInstance().getPropertyByName("aws.elastictranscoder.outputkeyprefix");
				
		alarmThreshold = Double.parseDouble(PropertiesManager.getInstance().getPropertyByName("aws.cloudwatch.alarm.threshold"));
		
		hlsPresets = mapper.readValue(PropertiesManager.getInstance().getPropertyByName("aws.elastictranscoder.hls.v3.playlist.presets"), Map.class);
		
		webmPresets = mapper.readValue(PropertiesManager.getInstance().getPropertyByName("aws.elastictranscoder.webm.presets"), Map.class);

	}
		
	private String originId;
	
	private String s3Domain;
	
	private String cloudfrontPriceClass;
	
	private String cloudfrontCallReference;
	
	private long cloudfrontMinttl;

	private String segmentDuration;

    private String outputKeyPrefix;
		
	private double alarmThreshold;
	
	private Map<String, Object> hlsPresets;
	
	private Map<String, Object> webmPresets;

	private AmazonIdentityManagement identityManagement = new AmazonIdentityManagementClient();
	
	private AmazonElasticTranscoder transcoderClient = new AmazonElasticTranscoderClient();
	
	private AmazonS3 s3client = new AmazonS3Client();
	
	private AmazonCloudFront cloudFrontClient = new AmazonCloudFrontClient();
			
	private TransferManager tm = new TransferManager(); 	
	
	private AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient();
	
	private AmazonSNSClient snsClient = new AmazonSNSClient();
	
	private AmazonSQSClient sqsClient = new AmazonSQSClient();

	public AmazonIdentityManagement getIdentityManagement() {
		return identityManagement;
	}

	public void setIdentityManagement(AmazonIdentityManagement identityManagement) {
		this.identityManagement = identityManagement;
	}

	public AmazonElasticTranscoder getTranscoderClient() {
		return transcoderClient;
	}

	public void setTranscoderClient(AmazonElasticTranscoder transcoderClient) {
		this.transcoderClient = transcoderClient;
	}

	public AmazonS3 getS3client() {
		return s3client;
	}

	public void setS3client(AmazonS3 s3client) {
		this.s3client = s3client;
	}

	public AmazonCloudFront getCloudFrontClient() {
		return cloudFrontClient;
	}

	public void setCloudFrontClient(AmazonCloudFront cloudFrontClient) {
		this.cloudFrontClient = cloudFrontClient;
	}

	public TransferManager getTm() {
		return tm;
	}

	public void setTm(TransferManager tm) {
		this.tm = tm;
	}

	public AmazonCloudWatchClient getCloudWatchClient() {
		return cloudWatchClient;
	}

	public void setCloudWatchClient(AmazonCloudWatchClient cloudWatchClient) {
		this.cloudWatchClient = cloudWatchClient;
	}

	public AmazonSNSClient getSnsClient() {
		return snsClient;
	}

	public void setSnsClient(AmazonSNSClient snsClient) {
		this.snsClient = snsClient;
	}

	public AmazonSQSClient getSqsClient() {
		return sqsClient;
	}

	public void setSqsClient(AmazonSQSClient sqsClient) {
		this.sqsClient = sqsClient;
	}
    
	public void createS3BucketIfNotExist(String bucketName, String regionName, boolean isOutputBucket) throws TranscodeException{
	    s3client.setRegion(Region.getRegion(Regions.fromName(regionName)));
	    try {
	        if(!(s3client.doesBucketExist(bucketName)))
	        {
	        	// Note that CreateBucketRequest does not specify region. So bucket is 
	        	// created in the region specified in the client.
	        	s3client.createBucket(new CreateBucketRequest(
	        			bucketName));
	        }
	        
	        if (isOutputBucket){
	        	String policyText = "{"
	        			  + "\"Id\": \"1\","
	        			  + "\"Version\": \"2012-10-17\","
	        			  + "\"Statement\": ["
	        			  + "  {"
	        			  + "    \"Sid\": \"1\","
	        			  + "    \"Action\": ["
	        			  + "    \"s3:GetObject\""
	        			  + "    ],"
	        			  + "  \"Effect\": \"Allow\","
	        			  + "  \"Resource\": \"arn:aws:s3:::"+bucketName+"/*\","
	        			  + "  \"Principal\": \"*\""
	        			  + "  }"
	        			  + "]"
	        			  + "}";
	        	SetBucketPolicyRequest setBucketPolicyRequest = new SetBucketPolicyRequest(bucketName, policyText);
	        	s3client.setBucketPolicy(setBucketPolicyRequest);
	        }	        
	     } catch (AmazonServiceException ase) {
	        logger.error("Caught an AmazonServiceException, which " +
	        		"means your request made it " +
	                "to Amazon S3, but was rejected with an error response" +
	                " for some reason.", ase);
	        throw new TranscodeException(ase.getMessage());
	    } catch (AmazonClientException ace) {
	        logger.error("Caught an AmazonClientException, which " +
	        		"means the client encountered " +
	                "an internal error while trying to " +
	                "communicate with S3, " +
	                "such as not being able to access the network.", ace);
	        throw new TranscodeException(ace.getMessage());
	    } catch (IllegalArgumentException ie) {
	        logger.error("Couldn't get the region", ie);
	    	throw new TranscodeException(ie.getMessage());
	    }
	}		
	
	public String createPipeLineIfNotExist(String pipelineName, String regionName, 
										 String inputBucketName, String outputBucketName,
										 String iamRole,
										 String topicArn,
										 String storageClass) throws TranscodeException {
		//http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/pipeline-settings.html		
		try {
			transcoderClient.setRegion(Region.getRegion(Regions.fromName(regionName)));			

			ListPipelinesResult pipelinesResult = transcoderClient.listPipelines();
			List<Pipeline> pipelines = pipelinesResult.getPipelines();
			boolean isPipelineExist = false;
			String existingPipelineId = null;
			for (Pipeline p : pipelines){
				if (p.getName().equals(pipelineName)){
					isPipelineExist = true;
					existingPipelineId = p.getId();
				}
			}
			
			if (!isPipelineExist){
				Notifications notifications = new Notifications()
					.withCompleted(topicArn)
					.withError(topicArn)
					.withWarning(topicArn)
					.withProgressing(topicArn);
				
				PipelineOutputConfig pipelineOutputConfig = new PipelineOutputConfig();
				pipelineOutputConfig.setStorageClass(storageClass);
				pipelineOutputConfig.setBucket(outputBucketName);
								
				CreatePipelineRequest createPipelineRequest = new CreatePipelineRequest()
																	.withName(pipelineName)
																	.withInputBucket(inputBucketName)
																	.withRole(iamRole)
																	.withContentConfig(pipelineOutputConfig)
																	.withThumbnailConfig(pipelineOutputConfig)
																	.withNotifications(notifications);
				
				CreatePipelineResult result = transcoderClient.createPipeline(createPipelineRequest);
				return result.getPipeline().getId();
			} else {
				logger.debug(String.format("pipleline with name %s already exist", pipelineName));
				return existingPipelineId;
			}
		} catch (IllegalArgumentException ie) {
	        logger.error("Couldn't get the region", ie);
	    	throw new TranscodeException(ie.getMessage());
	    }
	}

	//Key:
	//http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html
	public void uploadAssetToS3Bucket(String bucketName, String key, File rawAsset, String storageClass) throws TranscodeException{
		ObjectMetadata objectMetaData = new ObjectMetadata();
		objectMetaData.setContentLength(rawAsset.length());
		try {
			FileInputStream  fileInputStream = new FileInputStream(rawAsset);
			PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, fileInputStream, objectMetaData);
			if (storageClass.equalsIgnoreCase(StorageClass.ReducedRedundancy.name())) {
				putObjectRequest.setStorageClass(StorageClass.ReducedRedundancy);
			}
			
			final long fileSizeInBytes = rawAsset.length();
			
			putObjectRequest.setGeneralProgressListener(new ProgressListener() {
				
				private long bytesTransferred = 0;
				
				private int currentPercentage = 0;
				
				@Override
				public void progressChanged(ProgressEvent progressEvent) {
					bytesTransferred += progressEvent.getBytesTransferred();
					int percentTransferred = (int) (bytesTransferred * 100 / fileSizeInBytes);
					if (percentTransferred%10 == 0 && percentTransferred != currentPercentage) {
						logger.info("Transferred {}% of {} bytes.", percentTransferred, fileSizeInBytes);
						currentPercentage = percentTransferred;
					}
				}
			});
			
			Upload upload = tm.upload(putObjectRequest);
			if (upload!=null) {
				upload.waitForCompletion();				
				tm.shutdownNow(false);
			} else {
				logger.error("Did not get upload detail from S3 for asset with key " + key);
				throw new TranscodeException("Did not get upload detail from S3 for asset with key " + key);
			}
		} catch (IOException e) {
			throw new TranscodeException(e.getMessage());
		} catch (AmazonClientException | InterruptedException e) {
			throw new TranscodeException(e.getMessage());			
		}
	}

	public CreateJobResult createTranscodeJob(String pipelineId, String inputKey) throws TranscodeException {
		JobInput input = new JobInput().withKey(inputKey);
	    
	    List<CreateJobOutput> hlsJobOutputs = new ArrayList<>();
	    Iterator<String> hlsPresetKeys = hlsPresets.keySet().iterator();
	    List<String> hlsJobKeys = new ArrayList<>();	    		
	    while (hlsPresetKeys.hasNext()) {
	    	String hlsPresetKey = hlsPresetKeys.next();
	    	String hlsJobKey = "hls/" + hlsPresetKey;
	    	
		    CreateJobOutput hlsJob = new CreateJobOutput()
	        .withKey(hlsJobKey)
	        .withPresetId((String)hlsPresets.get(hlsPresetKey))
	        .withSegmentDuration(segmentDuration);
		    
		    hlsJobKeys.add(hlsJobKey);
		    hlsJobOutputs.add(hlsJob);		    
	    }
	    
	    // Setup master playlist which can be used to play using adaptive bitrate.
	    CreateJobPlaylist playlist = new CreateJobPlaylist()
	        .withName("hls") 
	        .withFormat("HLSv3")
	        .withOutputKeys(hlsJobKeys);
	    
	    List<CreateJobOutput> webmJobOutputs = new ArrayList<>();
	    Iterator<String> webmPresetKeys = webmPresets.keySet().iterator();
	    while (webmPresetKeys.hasNext()) {
	    	String webmPresetKey = webmPresetKeys.next(); 
		    CreateJobOutput webmJob = new CreateJobOutput()
	        .withKey(webmPresetKey)
	        .withPresetId((String)webmPresets.get(webmPresetKey));		    
		    webmJobOutputs.add(webmJob);
	    }

	    List<CreateJobOutput> outputs = new ArrayList<>();
	    outputs.addAll(hlsJobOutputs);
	    outputs.addAll(webmJobOutputs);
	    
	    // Create the job.
	    CreateJobRequest createJobRequest = new CreateJobRequest()
	        .withPipelineId(pipelineId)
	        .withInput(input)
	        .withOutputKeyPrefix(outputKeyPrefix + inputKey + "/")
	        .withOutputs(outputs)
	        .withPlaylists(playlist);

	    return transcoderClient.createJob(createJobRequest);
	}

	//http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/GettingStarted.html
	//http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/PrivateContent.html
	/*
	 * Create a special CloudFront user called an origin access identity.
	 * Give the origin access identity permission to read the objects in your bucket.
	 * Remove permission for anyone else to use Amazon S3 URLs to read the objects.
	 */
	public Distribution createCDNIfNotExist() {
		ListDistributionsRequest listDistributionsRequest = new ListDistributionsRequest();		
		ListDistributionsResult listDistributionsResult = cloudFrontClient.listDistributions(listDistributionsRequest);
		for (DistributionSummary d : listDistributionsResult.getDistributionList().getItems()) {
			for (Origin o : d.getOrigins().getItems()) {
				if (o.getDomainName().equals(s3Domain)) {
					return new Distribution().withId(d.getId()).withDomainName(d.getDomainName());
				}
			}
		}
		
		//we want a web distribution, streaming distribution is for rtmp
		
		CreateDistributionRequest request = new CreateDistributionRequest();
		request.setDistributionConfig(getDistributionConfig());
		CreateDistributionResult distributionResult = cloudFrontClient.createDistribution(request);
		return distributionResult.getDistribution();
	}
	
	private DistributionConfig getDistributionConfig() {
		DistributionConfig config = new DistributionConfig();
		Origin origin = new Origin();
		S3OriginConfig s3OriginConfig = new S3OriginConfig()
			.withOriginAccessIdentity("");
		origin.withId(originId)
			.withDomainName(s3Domain)
			.withS3OriginConfig(s3OriginConfig); 
		Origins origins = new Origins();
		origins.withItems(origin)
			.withQuantity(1);
		config.setOrigins(origins);
		config.setPriceClass(cloudfrontPriceClass);
		config.setEnabled(true); //enabled to accept end user request for content
		config.setDefaultRootObject("");
		LoggingConfig loggingConfig = new LoggingConfig()
			.withEnabled(false)
			.withBucket("")
			.withPrefix("")
			.withIncludeCookies(false);
		config.setLogging(loggingConfig);
		config.setAliases(new Aliases().withQuantity(0)); //no aliases
		config.setCacheBehaviors(new CacheBehaviors().withQuantity(0)); //no cacheBehaviors
		config.setComment("");
		AllowedMethods allowedMethods = new AllowedMethods();
		allowedMethods.withItems(Method.GET, Method.HEAD, Method.OPTIONS).withQuantity(3);
		DefaultCacheBehavior defaultCacheBehavior = new DefaultCacheBehavior()
			.withAllowedMethods(allowedMethods)
			.withTargetOriginId(originId) //This matches the origin we created earlier
			.withMinTTL(cloudfrontMinttl)
			.withViewerProtocolPolicy(ViewerProtocolPolicy.AllowAll)
			.withTrustedSigners(new TrustedSigners().withEnabled(false).withQuantity(0))
			.withForwardedValues(new ForwardedValues()
				.withCookies(new CookiePreference().withForward("none"))
				.withQueryString(false)); //no forwarded values
		config.setDefaultCacheBehavior(defaultCacheBehavior);
		config.setCallerReference(cloudfrontCallReference);
		return config;
	}
		
	public String createIAMRoleIfNotExist(String roleName) {
		GetRoleRequest getRoleRequest = new GetRoleRequest()
			.withRoleName(roleName);
		try {
			GetRoleResult getRoleResult = identityManagement.getRole(getRoleRequest);
			return getRoleResult.getRole().getArn();
		} catch (NoSuchEntityException e) {
			CreateRoleRequest request = new CreateRoleRequest().withRoleName(roleName)
					.withAssumeRolePolicyDocument("{\"Version\": \"2008-10-17\","
							+ "\"Statement\": ["
							+ "{"
							+ "\"Sid\": \"1\","
							+ "\"Effect\": \"Allow\","
							+ "\"Principal\": {"
							+ "\"Service\": \"elastictranscoder.amazonaws.com\""
							+ "},"
							+ "\"Action\": \"sts:AssumeRole\""
							+ "}"
							+ "]"
							+ "}");
			CreateRoleResult roleResult = identityManagement.createRole(request);

			PutRolePolicyRequest putRolePolicyRequest = new PutRolePolicyRequest()
				.withPolicyName("s3video_generated_policy")
				.withPolicyDocument("{\"Version\":\"2008-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\",\"Action\":[\"s3:ListBucket\",\"s3:Put*\",\"s3:Get*\",\"s3:*MultipartUpload*\"],\"Resource\":\"*\"},{\"Sid\":\"2\",\"Effect\":\"Allow\",\"Action\":\"sns:Publish\",\"Resource\":\"*\"},{\"Sid\":\"3\",\"Effect\":\"Deny\",\"Action\":[\"s3:*Policy*\",\"sns:*Permission*\",\"sns:*Delete*\",\"s3:*Delete*\",\"sns:*Remove*\"],\"Resource\":\"*\"}]}")
				.withRoleName(roleName);
			identityManagement.putRolePolicy(putRolePolicyRequest);
			
			return roleResult.getRole().getArn();
		}
	}

	public void deleteAsset(String bucketName, String key) {
		s3client.deleteObject(bucketName, key);
	}

	public void deleteTranscodedAsset(String bucketName, String key) {
		for (S3ObjectSummary file : s3client.listObjects(bucketName, outputKeyPrefix+key).getObjectSummaries()){
			s3client.deleteObject(bucketName, file.getKey());
		}
	}

	public void removePublicAccessToBucket(String bucketName) {
		s3client.deleteBucketPolicy(bucketName);
	}

	public void pausePipeLine(String pipelineId) {
		UpdatePipelineStatusRequest request = new UpdatePipelineStatusRequest()
			.withId(pipelineId)
			.withStatus("Paused");
		transcoderClient.updatePipelineStatus(request);		
	}

	public void disableCDN(String distributionId) {
		GetDistributionRequest getDistributionRequest = new GetDistributionRequest().withId(distributionId);
		GetDistributionResult getDistributionResult = cloudFrontClient.getDistribution(getDistributionRequest);
		
		DistributionConfig distributionConfig = getDistributionResult.getDistribution().getDistributionConfig().withEnabled(false);
		UpdateDistributionRequest updateDistributionRequest = new UpdateDistributionRequest()
			.withId(distributionId)
			.withDistributionConfig(distributionConfig)
			.withIfMatch(getDistributionResult.getETag());
		cloudFrontClient.updateDistribution(updateDistributionRequest);
	}
	
	public List<String> listKeysInInputBucket(String bucketName) {
		List<String> videoKeys = new ArrayList<>();
		for (S3ObjectSummary file : s3client.listObjects(bucketName).getObjectSummaries()) {
			videoKeys.add(file.getKey());
		}
		return videoKeys;		
	}

	public List<String> listKeysInOutputBucket(String bucketName) {
		List<String> videoKeys = new ArrayList<>();
		for (S3ObjectSummary file : s3client.listObjects(bucketName, outputKeyPrefix).getObjectSummaries()) {
			videoKeys.add(file.getKey());
		}
		return videoKeys;		
	}
		
	public void createAlarmIfNotAlreadyExist(String topicArn) {		
		List<Dimension> dimensions = new ArrayList<>();
		dimensions.add(new Dimension().withName("Currency").withValue("USD"));
		PutMetricAlarmRequest request = new PutMetricAlarmRequest();
		request.setAlarmName("s3video Billing Alarm");
		request.setAlarmActions(Arrays.asList(topicArn));
		request.setMetricName("EstimatedCharges");
		request.setNamespace("AWS/Billing");
		request.setDimensions(dimensions);
		request.setThreshold(alarmThreshold);
		request.setPeriod(SIX_HOUR_IN_SECONDS);
		request.setStatistic("Maximum");
		request.setComparisonOperator("GreaterThanThreshold");
		request.setEvaluationPeriods(1);
		cloudWatchClient.putMetricAlarm(request);
	}	
	
	public String createNotificationTopicIfNotExist(String topicName){
		CreateTopicRequest createTopicRequest = new CreateTopicRequest(topicName);
		CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);
		return createTopicResult.getTopicArn();
	}
	
	public String subscribeEmailToTopic(String topicArn, String emailAddress){
		SubscribeRequest subRequest = new SubscribeRequest(topicArn, "email", emailAddress);
		return snsClient.subscribe(subRequest).getSubscriptionArn();
	}

	
	public String createMessageQueue(String queueName) {
		CreateQueueResult createQueueResult = sqsClient.createQueue(queueName);
		return createQueueResult.getQueueUrl();
	}
	
	public String subscribeQueueToTopic(String snsTopicArn, String sqsQueueUrl){		
        Map<String, String> queueAttributes = sqsClient.getQueueAttributes(new GetQueueAttributesRequest(sqsQueueUrl)
                .withAttributeNames(QueueAttributeName.QueueArn.toString())).getAttributes();
        String sqsQueueArn = queueAttributes.get(QueueAttributeName.QueueArn.toString());

        Policy policy = new Policy().withStatements(
                new Statement(Effect.Allow)
                    .withId("topic-subscription-" + snsTopicArn)
                    .withPrincipals(Principal.AllUsers)
                    .withActions(SQSActions.SendMessage)
                    .withResources(new Resource(sqsQueueArn))
                    .withConditions(ConditionFactory.newSourceArnCondition(snsTopicArn)));

        logger.debug("Policy: " + policy.toJson());

        queueAttributes = new HashMap<String, String>();
        queueAttributes.put(QueueAttributeName.Policy.toString(), policy.toJson());
        sqsClient.setQueueAttributes(new SetQueueAttributesRequest(sqsQueueUrl, queueAttributes));

        SubscribeResult subscribeResult =
                snsClient.subscribe(new SubscribeRequest()
                    .withEndpoint(sqsQueueArn)
                    .withProtocol("sqs")
                    .withTopicArn(snsTopicArn));
        return subscribeResult.getSubscriptionArn();
	}
	
	public List<JobStatusNotification> pollMessageFromQueueByJobId(String queueUrl, String jobId) {
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
        .withQueueUrl(queueUrl)
        .withMaxNumberOfMessages(MAX_NUMBER_OF_MESSAGES)
        .withVisibilityTimeout(VISIBILITY_TIMEOUT)
        .withWaitTimeSeconds(WAIT_TIME_SECONDS);
				
		List<JobStatusNotification> jobStatusNotifications = new ArrayList<>();
		
		for (Message message : sqsClient.receiveMessage(receiveMessageRequest).getMessages()) {
			try {
				JobStatusNotification jobStatusNotification = parseMessage(message.getBody());
				
				if (jobStatusNotification.getJobId().equalsIgnoreCase(jobId)) {
					jobStatusNotifications.add(jobStatusNotification);
					sqsClient.deleteMessage(new DeleteMessageRequest().withQueueUrl(queueUrl).withReceiptHandle(message.getReceiptHandle()));
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}		
		return jobStatusNotifications;		
	}
	
	public JobStatusNotification parseMessage(String messageBody) throws JsonParseException, JsonMappingException, IOException {
		Notification<JobStatusNotification> notification = mapper.readValue(messageBody, new TypeReference<Notification<JobStatusNotification>>() {});
        return notification.getMessage();						
	}
}