package de.lmu.ifi.bio.crco.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class CroCoLogger {
//	private static String configFile = new String("conf/app.config");
	private static Logger logger;
	private CroCoLogger(){}
	
	public static Logger getLogger(InputStream configFile) {
		if ( logger == null){
			//String path = Sessions.getCurrent().getWebApp().getRealPath(configFile);
			//System.out.println("Starting logger:\t" + new File(path).exists());
			 
		//	 PropertyConfigurator.configure(path);
			Properties props = new Properties();
			try {
				props.load(configFile);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			PropertyConfigurator.configure(props);
			logger = Logger.getRootLogger();
			
			//	logger =  org.apache.log4j.Logger.getRootLogger();
			logger.info("Logger started");
			
			//logger.info("test");
		}
		return logger;
	}
	
	public static Logger getLogger()  {
		if ( logger == null){
		
			String file = "connet.config";
			
			InputStream stream = CroCoLogger.class.getClassLoader().getResourceAsStream(file);
			if ( stream == null){
				throw new RuntimeException("Can not find:\t properties (" + file + ")");
			}
			return getLogger(stream);
		}
		return logger;
	}
	

}