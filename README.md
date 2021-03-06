# Stream Video From S3

## What s3video can do for you
  - Upload your video to s3 for html5 video streaming, or create animated gif from your video.
  - Create and configure elastic transcoder pipeline to transcode your video into HLS, webm or animated gif.
  - Create and configure cloud front to distribute your content.
  - Create aws billing alarm to help you monitor your transcoding and streaming cost.
  - Stream your video on any web page. For example [stream s3video on Jekyll site.](https://gist.github.com/TimShi/a48fa83abbc8a0242557).

## Download or Build s3video

Download the latest build from the releases page.

If you want to build s3video, you will need Java and maven. To build s3video, clone the repository and run 

```sh
$ mvn clean package
```

This will generate a s3video-{version}.tar.gz file in the target directory. Unzip this file to the location where you want s3_video installed.

## Setting Up
  - In order for s3video to work with AWS web services, create API credentials that have sufficient permissions. Rename the custom_template.properties file to custom.properties. Put the key and secret into the custom.properties file.
  - Fill in the value for the other properties.

The following permission is required, attach this policy to your user. Replace account-id with your account id. Replace aws-s3-bucket-in-name and aws-s3-bucket-out-name with the corresponding values you put in the properties file.  

  - s3
  - cloudfront
  - elastic transcoder
  - iam
  - sns
  - sqs
  - cloudwatch

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "iam:GetRole",
                "iam:PutRolePolicy",
                "iam:CreateRole",
                "iam:PassRole"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:iam::account-id:role/s3video_transcode_role"
            ]
        },
        {
            "Action": [
                "elastictranscoder:ListPipelines",
                "elastictranscoder:CreatePipeline",
                "elastictranscoder:CreateJob",
                "elastictranscoder:UpdatePipelineStatus"
            ],
            "Effect": "Allow",
            "Resource": [
                "*"
            ]
        },
        {
            "Action": [
                "cloudfront:ListDistributions",
                "cloudfront:CreateDistribution",
                "cloudfront:GetDistribution",
                "cloudfront:UpdateDistribution"
            ],
            "Effect": "Allow",
            "Resource": [
                "*"
            ]
        },
        {
            "Action": [
                "cloudwatch:PutMetricAlarm"
            ],
            "Effect": "Allow",
            "Resource": [
                "*"
            ]
        },
        {
            "Action": [
                "sns:CreateTopic",
                "sns:Subscribe"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:sns:*:account-id:transcode_notification",
                "arn:aws:sns:*:account-id:s3video_billing_notification"
            ]
        },
        {
            "Action": [
                "sqs:CreateQueue"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:sqs:*:account-id"
            ]
        },
        {
            "Action": [
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:AddPermission",
                "sqs:GetQueueAttributes",
                "sqs:SetQueueAttributes"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:sqs:*:account-id:s3video_queue"
            ]
        },
        {
            "Action": [
                "s3:ListBucket",
                "s3:CreateBucket",
                "s3:PutBucketPolicy",
                "s3:DeleteBucketPolicy",
                "s3:PutObject",
                "s3:DeleteObject"
            ],
            "Effect": "Allow",
            "Resource": [
                "arn:aws:s3:::aws-s3-bucket-out-name",
                "arn:aws:s3:::aws-s3-bucket-in-name",
                "arn:aws:s3:::aws-s3-bucket-out-name/*",
                "arn:aws:s3:::aws-s3-bucket-in-name/*"
            ]
        }
    ]
}
```

  - Put an email address in the custom.properties file to receive billing alarms. 
  - The billing alarm is set to $1 by default. You can change it by editing the aws.cloudwatch.alarm.threshold in default.properties.

## Usage
### Initialize AWS web services
The configure command will create and configure all the AWS web services needed to transcode and stream your vide.
```sh
./s3_video.sh configure
```
This includes creating two s3 bucket for the source video and transcoded video; an elastic transcoder pipleline to process the source video; an IAM role for the elastic transcoder to have permission to work with s3 and sns; one cloud front distribution to distribute the video; one sqs queue to process the transcode status updates; cloudwatch metric for monitoring overall spend and sns notification topics for billing notification.

### List settings
This command will list your s3video settings. The most important information is the cloud front domain.
You will use this domain to access the streaming videos.
```sh
./s3_video.sh list --setting
```

### Upload video 
The push command will upload your video to s3 and starts the transcoding process. The command will exit when transcoding is complete. Replace "my_video.mp4" with the path to your video.
If you supply the path to a directory, all the videos in your directory will be transcoded.
```sh
./s3_video.sh push my_video.mp4
```

### Share uploaded videos
Navigate to {cloudFrontDomain}/{videoName}/player.html to get the html5 video player and share the video.

### Create gif 
The push command will upload your video to s3 and starts the transcoding process. The command will exit when transcoding is complete. Replace "my_video.mp4" with the path to your video.
```sh
./s3_video.sh --gif push my_video.mp4
```

### List uploaded videos
This command will list the transcoded videos. Append the path listed here to the cloud front domain, and your video will be streaming in the browser. (Use the HLS path for Safari and webm for Chrome and Firefox).
```sh
./s3_video.sh list --transocded
```
This command will list the source videos.
```sh
./s3_video.sh list --source
```

### Delete videos
This command will delete both the original and the transcoded videos. Use the --transcoded or --source flag to delete only one. Replace "myvideo" with the name of your video. This should match the name you see when you use the list command.
```sh
./s3_video.sh delete my_video
```

### Stop
This command stops the AWS web services that is responsible for transcoding and streaming video. You may want to use this to temporarily shut down your streams. The transcode pipleine will also be paused.
```sh
./s3_video.sh stop
```
To resume after stopping, you need to do the following (via the AWS console):
  - enable cloudfront distribution
  - enable access to the output bucket by adding the following policy to the bucket.

```json
{
	"Id": "1",
	"Version": "2012-10-17",
	"Statement": [
		{
			"Sid": "1",
		 	"Action": ["s3:GetObject"],
		 	"Effect": "Allow",
		 	"Resource": "arn:aws:s3:::aws-s3-bucket-out-name/*",
			"Principal": "*"
		}
	]
}
```
  - activate the elastic transcoder pipeline.
