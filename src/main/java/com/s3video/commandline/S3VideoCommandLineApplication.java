package com.s3video.commandline;

import java.io.IOException;
import java.util.List;

import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudfront.AmazonCloudFrontClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.s3video.commandline.adapter.AWSAdapter;
import com.s3video.commandline.properties.PropertiesManager;
import com.s3video.commandline.repository.TranscoderRepository;
import com.s3video.commandline.service.InvalidNameException;
import com.s3video.commandline.service.SettingsExistException;
import com.s3video.commandline.service.TemplateService;
import com.s3video.commandline.service.TranscodeException;
import com.s3video.commandline.service.TranscodeService;

public class S3VideoCommandLineApplication {

	final static Logger logger = LoggerFactory.getLogger(S3VideoCommandLineApplication.class);

	public static void main(String[] args) {
		try {
			PropertiesManager propertiesManager = PropertiesManager.getInstance();
			
			System.setProperty("aws.accessKeyId", propertiesManager.getPropertyByName("aws.accessKeyId"));
	    	System.setProperty("aws.secretKey", propertiesManager.getPropertyByName("aws.secretKey"));		
			
	    	AWSAdapter awsAdapter = new AWSAdapter();
	    	awsAdapter.setIdentityManagement(new AmazonIdentityManagementClient());    	
	    	awsAdapter.setTranscoderClient(new AmazonElasticTranscoderClient());
	    	awsAdapter.setS3client(new AmazonS3Client());
	    	awsAdapter.setCloudFrontClient(new AmazonCloudFrontClient());
	    	awsAdapter.setTm(new TransferManager());
	    	awsAdapter.setCloudWatchClient(new AmazonCloudWatchClient());
	    	awsAdapter.setSnsClient(new AmazonSNSClient());
	    	awsAdapter.setSqsClient(new AmazonSQSClient());
	    	
	    	TranscodeService transcodeService = new TranscodeService();
	    	transcodeService.setAwsAdapter(awsAdapter);
	    	transcodeService.setTranscoderRepository(new TranscoderRepository());

	    	VelocityEngine velocityEngine = new VelocityEngine();
	    	velocityEngine.init();
	    	TemplateService templateService = new TemplateService();
	    	templateService.setAwsAdapter(awsAdapter);
	    	templateService.setTranscoderRepository(new TranscoderRepository());
	    	templateService.setVelocityEngine(velocityEngine);

			if (args.length >= 1) {
				if (args[0].equalsIgnoreCase("configure")) {
					if (args.length == 2 && args[1].equalsIgnoreCase("--force")) {
						transcodeService.configure(true);
						logger.info("s3video configured.");
					} else if (args.length == 1) {
						transcodeService.configure(false);
						logger.info("s3video configured.");
					} else {
						logger.info("configure [--force]");						
					}
				} else if (args[0].equalsIgnoreCase("list")) {
					if (args.length == 2) {
						String flag = args[1];
						if (!(flag.equalsIgnoreCase("--source") || flag.equalsIgnoreCase("--transcoded") || flag.equalsIgnoreCase("--setting"))) {
							logger.info("list [--source | --transcoded | --setting]");
							return;
						}
						if (flag.equalsIgnoreCase("--source")) {
							logger.info("list source videos");
							transcodeService.listSourceVideos();
						} else if (flag.equalsIgnoreCase("--transcoded")) {
							logger.info("list transcoded videos");
							transcodeService.listTranscodedVideos();						
						} else if (flag.equalsIgnoreCase("--setting")) {
							logger.info("list setting");
							transcodeService.listSettings();						
						}					
					} else {
						logger.info("list [--source | --transcoded]");
					}				
				}else if (args[0].equalsIgnoreCase("push")) {
					if (args.length == 2) {
						String sourceFilePath = args[1];
						logger.info("pushing video:" + sourceFilePath);
						List<String> keys = transcodeService.push(sourceFilePath);
						
						for (String key : keys) {
							templateService.uploadPlaybackHtml(key);
						}
					} else if (args.length == 3) {
						String flag = args[1];
						if (flag.equalsIgnoreCase("--gif")) {
							String sourceFilePath = args[2];						
							//GIF preset 
							//1351620000001-100200						
							transcodeService.pushGif(sourceFilePath);							
						} else if (flag.equalsIgnoreCase("--template")) {
							String videoName = args[2];						
							templateService.uploadPlaybackHtml(videoName);						
						} else {
							logger.info("push [--gif | --template] {sourceFileOrDirectoryPath | videoName}");
							return;
						}
					} else {
						logger.info("push [--gif | --template] {sourceFileOrDirectoryPath | videoName}");
						return;
					}				
				} else if (args[0].equalsIgnoreCase("delete")) {
					if (args.length == 2) {
						String key = args[1];
						logger.info("delete video:" + key);
						transcodeService.deleteAll(key);
					} else if (args.length == 3) {
						String flag = args[1];
						if (!(flag.equalsIgnoreCase("--source") || flag.equalsIgnoreCase("--transcoded"))) {
							logger.info("delete [--source | --transcoded] {sourceFileName}");
							return;
						}
						String key = args[2];
						if (flag.equalsIgnoreCase("--source")) {
							logger.info("delete source video:" + key);
							transcodeService.deleteSource(key);
						} else if (flag.equalsIgnoreCase("--transcoded")) {
							logger.info("delete transcoded video:" + key);
							transcodeService.deleteTranscoded(key);						
						}
					} else {
						logger.info("delete [--source | --transcoded] {sourceFileName}");
					}
				} else if (args[0].equalsIgnoreCase("stop")) {
					logger.info("stopping transcoding and streaming");
					transcodeService.stop();
				} else {
					logger.info("configure");
					logger.info("push {sourceFilePath} ");
					logger.info("list [--source | --transcoded | --setting]");
					logger.info("delete [--source | --transcoded] {sourceFileName}");
					logger.info("stop");
					return;
				}
			} else {
				logger.info("configure"); 
				logger.info("list"); 
				logger.info("push {sourceFilePath}"); 
				logger.info("delete [--source | --transcoded] {name}");
			}	
			
			propertiesManager.saveProperties();
			awsAdapter.shutDownTransferManager();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (TranscodeException e) {
			logger.error(e.getMessage(), e);
		} catch (SettingsExistException e) {
			logger.error(e.getMessage(), e);
		} catch (InvalidNameException e) {
			logger.error(e.getMessage(), e);
		}
	}
}