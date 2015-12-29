package com.s3video.commandline.service;

public class SettingsExistException extends Exception{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1512528924294709679L;

	public SettingsExistException(String message){
		super(message);
	}
}
