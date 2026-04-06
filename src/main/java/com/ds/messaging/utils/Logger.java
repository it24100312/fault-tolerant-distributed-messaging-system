package com.ds.messaging.utils;

import org.slf4j.LoggerFactory;

/**
 * Utility wrapper around SLF4J for structured logging.
 * 
 * TODO: Implement logging methods
 */
public class Logger {
    /**
     * Get logger instance for a class
     */
    public static Logger getInstance() {
        String className = Thread.currentThread().getStackTrace()[2].getClassName();
        org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(className);
        return new Logger(slf4jLogger);
    }
    
    private final org.slf4j.Logger slf4jLogger;
    
    public Logger(org.slf4j.Logger slf4jLogger) {
        this.slf4jLogger = slf4jLogger;
    }
    
    /**
     * Log info message with format string
     */
    public void info(String msg, Object... args) {
        // TODO: Implement info logging
        if (args.length == 0) {
            slf4jLogger.info(msg);
        } else {
            slf4jLogger.info(msg, args);
        }
    }
    
    /**
     * Log error message with exception
     */
    public void error(String msg, Throwable e) {
        // TODO: Implement error logging
        slf4jLogger.error(msg, e);
    }
    
    /**
     * Log error message
     */
    public void error(String msg, Object... args) {
        // TODO: Implement error logging
        slf4jLogger.error(msg, args);
    }
    
    /**
     * Log debug message
     */
    public void debug(String msg, Object... args) {
        // TODO: Implement debug logging
        if (args.length == 0) {
            slf4jLogger.debug(msg);
        } else {
            slf4jLogger.debug(msg, args);
        }
    }
    
    /**
     * Log warning message
     */
    public void warn(String msg, Object... args) {
        // TODO: Implement warn logging
        if (args.length == 0) {
            slf4jLogger.warn(msg);
        } else {
            slf4jLogger.warn(msg, args);
        }
    }
}
