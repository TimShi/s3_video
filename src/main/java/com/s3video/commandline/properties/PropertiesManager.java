package com.s3video.commandline.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesManager {
	
	private static PropertiesManager instance = null;
	
	private Properties defaultProps;
	
	private Properties applicationProps;
	
	private PropertiesManager() throws IOException {
		super();
		
		defaultProps = new Properties();
		
		FileInputStream in = new FileInputStream("default.properties");
		defaultProps.load(in);
		in.close();

		in = new FileInputStream("custom.properties");
		defaultProps.load(in);
		in.close();
		
		// create application properties with default
		applicationProps = new Properties(defaultProps);
		
		File appPropertiesFile = new File("application.properties");
		
		if (appPropertiesFile.exists()) {		
			in = new FileInputStream("application.properties");
			applicationProps.load(in);
			in.close();				
		}
	}
	
	public static PropertiesManager getInstance() throws IOException {
		if (instance == null) {
			instance = new PropertiesManager();
		}
		return instance;
	}
	
	public String getPropertyByName(String key) {
		return applicationProps.getProperty(key);		
	}
	
	public void setProperty(String key, String value) {
		applicationProps.setProperty(key, value);		
	}
	
	public void saveProperties() throws IOException{
		FileOutputStream out = new FileOutputStream("application.properties");
		applicationProps.store(out, null);
		out.close();
	}
}
