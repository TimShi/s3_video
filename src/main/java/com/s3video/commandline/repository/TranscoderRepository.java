package com.s3video.commandline.repository;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.s3video.commandline.model.Transcoder;
import com.s3video.commandline.properties.PropertiesManager;

public class TranscoderRepository {
	final static Logger logger = LoggerFactory.getLogger(TranscoderRepository.class);
			
	private String createSetterNameFromFieldName(String fieldName) {
		return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}

	private String createGetterNameFromFieldName(String fieldName) {
		return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}

	public Transcoder getTranscoder() throws IOException {
		Transcoder transcoder = new Transcoder();
		
		Field[] fields = Transcoder.class.getDeclaredFields();
		
		PropertiesManager propertiesManager = PropertiesManager.getInstance();
		
		for (int i = 0; i<fields.length; i++) {
			String fieldValue = propertiesManager.getPropertyByName("Transcoder." + fields[i].getName());
			try {
				Method setterMethod = Transcoder.class.getMethod(createSetterNameFromFieldName(fields[i].getName()), String.class);
				setterMethod.invoke(transcoder, fieldValue);
			} catch (NoSuchMethodException e) {
				logger.error(e.getMessage(), e);
			} catch (SecurityException e) {
				logger.error(e.getMessage(), e);
			} catch (IllegalAccessException e) {
				logger.error(e.getMessage(), e);
			} catch (IllegalArgumentException e) {
				logger.error(e.getMessage(), e);			
			} catch (InvocationTargetException e) {
				logger.error(e.getMessage(), e);			
			}
		}		
		return transcoder;
	}
	
	public void save(Transcoder transcoder) throws IOException {

		Field[] fields = Transcoder.class.getDeclaredFields();
		
		PropertiesManager propertiesManager = PropertiesManager.getInstance();
		
		for (int i = 0; i<fields.length; i++) {
			try {
				Method getterMethod = Transcoder.class.getMethod(createGetterNameFromFieldName(fields[i].getName()));				
				String fieldValue = (String)getterMethod.invoke(transcoder);
				propertiesManager.setProperty("Transcoder." + fields[i].getName(), fieldValue);
			} catch (NoSuchMethodException e) {
				logger.error(e.getMessage(), e);
			} catch (SecurityException e) {
				logger.error(e.getMessage(), e);
			} catch (IllegalAccessException e) {
				logger.error(e.getMessage(), e);
			} catch (IllegalArgumentException e) {
				logger.error(e.getMessage(), e);			
			} catch (InvocationTargetException e) {
				logger.error(e.getMessage(), e);			
			}
		}		
	}
}
