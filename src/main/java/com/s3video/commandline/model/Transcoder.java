package com.s3video.commandline.model;


public class Transcoder {	
	private String isInitialized;
	private String pipelineId;
	private String distributionDomainName;
	private String distributionId;
	private String notificationTopicArn;
	private String billingNotificationTopicArn;
	private String notificationSubscriptionArn;
	private String billingNotificationSubscriptionArn;
	private String notificationQueueUrl;
	
	public String getNotificationQueueUrl() {
		return notificationQueueUrl;
	}

	public void setNotificationQueueUrl(String notificationQueueUrl) {
		this.notificationQueueUrl = notificationQueueUrl;
	}

	public String getBillingNotificationSubscriptionArn() {
		return billingNotificationSubscriptionArn;
	}

	public void setBillingNotificationSubscriptionArn(
			String billingNotificationSubscriptionArn) {
		this.billingNotificationSubscriptionArn = billingNotificationSubscriptionArn;
	}

	public String getNotificationSubscriptionArn() {
		return notificationSubscriptionArn;
	}

	public void setNotificationSubscriptionArn(String notificationSubscriptionArn) {
		this.notificationSubscriptionArn = notificationSubscriptionArn;
	}

	public String getDistributionDomainName() {
		return distributionDomainName;
	}

	public void setDistributionDomainName(String distributionDomainName) {
		this.distributionDomainName = distributionDomainName;
	}

	public String getIsInitialized() {
		return isInitialized;
	}

	public void setIsInitialized(String isInitialized) {
		this.isInitialized = isInitialized;
	}
	
	public String getPipelineId() {
		return pipelineId;
	}

	public void setPipelineId(String pipelineId) {
		this.pipelineId = pipelineId;
	}
	
	public String getNotificationTopicArn() {
		return notificationTopicArn;
	}

	public void setNotificationTopicArn(String notificationTopicArn) {
		this.notificationTopicArn = notificationTopicArn;
	}
	
	public String getBillingNotificationTopicArn() {
		return billingNotificationTopicArn;
	}

	public void setBillingNotificationTopicArn(String billingNotificationTopicArn) {
		this.billingNotificationTopicArn = billingNotificationTopicArn;
	}

	public String getDistributionId() {
		return distributionId;
	}

	public void setDistributionId(String distributionId) {
		this.distributionId = distributionId;
	}
}