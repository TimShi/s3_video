package com.s3video.commandline.service;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.s3video.commandline.adapter.AWSAdapter;
import com.s3video.commandline.model.Transcoder;
import com.s3video.commandline.properties.PropertiesManager;
import com.s3video.commandline.repository.TranscoderRepository;

public class TemplateService {
	final static Logger logger = LoggerFactory.getLogger(TemplateService.class);
	
	public TemplateService() throws IOException {		
		super();
		
		PropertiesManager propertiesManager = PropertiesManager.getInstance();
		
		outputBucketName = propertiesManager.getPropertyByName("aws.s3.bucket.out.name");
		storageClass = propertiesManager.getPropertyByName("aws.elastictranscoder.pipeline.storageclass");
	}
	
	private String outputBucketName;

	private String storageClass;

	private VelocityEngine velocityEngine;

	private AWSAdapter awsAdapter;	
	
	private TranscoderRepository transcoderRepository;

	public VelocityEngine getVelocityEngine() {
		return velocityEngine;
	}

	public void setVelocityEngine(VelocityEngine velocityEngine) {
		this.velocityEngine = velocityEngine;
	}
	
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

	public void uploadPlaybackHtml(String key) throws IOException, TranscodeException {
		Transcoder transcoder = transcoderRepository.getTranscoder();

		Template playerTemplate = velocityEngine.getTemplate("player.vm");
		VelocityContext context = new VelocityContext();
		context.put("domain", transcoder.getDistributionDomainName());
		context.put("name", key);
		StringWriter writer = new StringWriter();		
		playerTemplate.merge(context, writer);

		logger.info("Uploading player html");
		
		awsAdapter.uploadTextToS3Bucket(outputBucketName, key + "/player.html", writer.toString(), storageClass);

		writer = new StringWriter();
		Template videoTemplate = velocityEngine.getTemplate("video.vm");
		videoTemplate.merge(context, writer);

		logger.info("Uploading video html");

		awsAdapter.uploadTextToS3Bucket(outputBucketName, key + "/video.html", writer.toString(), storageClass);
	}
}