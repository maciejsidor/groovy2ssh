package com.googlecode.msidor.groovy2ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class SSHUserInfo implements UserInfo, UIKeyboardInteractive
{
	
	/**
	 * Logger object from sl4j framework
	 */
	private Logger     logger = LoggerFactory.getLogger( SSHManager.class );	
	
	public String getPassword()
	{ 
	  logger.debug("Prompting for password");	  
	  return null;
	}

	public boolean promptYesNo(String str)
	{ 
	  logger.debug("Prompting yes/no: "+str);
	  return false; 
	}

	public String getPassphrase()
	{ 
		logger.debug("Prompting for passphrase");
		return null; 
	}

	public boolean promptPassphrase(String message)
	{ 
		logger.debug("Prompting for passphrase: "+message);
		return false; 
	}

	public boolean promptPassword(String message)
	{ 
		logger.debug("Prompting for password: "+message);
		return false; 
	}

	public void showMessage(String message)
	{ 
		logger.debug("Prompting message: "+message);
	}

	public String[] promptKeyboardInteractive(String destination,String name,String instruction,String[] prompt,boolean[] echo)
	{
		logger.debug("promptKeyboardInteractive: "+destination+", "+name+", "+instruction+", "+prompt+", "+echo);
		return null;
	}	

}