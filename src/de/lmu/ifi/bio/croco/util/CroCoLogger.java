package de.lmu.ifi.bio.croco.util;

import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class CroCoLogger {
	
	private static Logger logger;
	private CroCoLogger(){}
	
	public static Logger getLogger()  {
		if ( logger == null){
			boolean error = false;
			try{
				Properties props = CroCoProperties.getInstance().getProperties();
				PropertyConfigurator.configure(props);
			}catch(IOException e){
				error = true;
			}
			
			logger = Logger.getRootLogger();
			
			logger.info("Logger started");
			if ( error) logger.warn("Cannot read croco config file");
		}
		return logger;
	}
	public static void info(String msg, Object ... format)
	{
	    getLogger().info(String.format(msg,format));
	}

    public static void debug(String msg, Object ... format) {
        getLogger().debug(String.format(msg,format));
    }
    public static void fatal(String msg, Object ... format) {
        getLogger().fatal(String.format(msg,format));
    }
    
    public static void error(String msg, Object ... format) {
        getLogger().error(String.format(msg,format));
    }
}
