/*
Copyright 2014 Maciej SIDOR [maciejsidor@gmail.com]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.	
 */
package com.googlecode.msidor.groovy2ssh;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * @author Maciej SIDOR
 *
 * This object that allows you to execute shell commands through groovy script via SSH connection.
 * The SSH connection is handled through JSCH framework.
 * 
 * Here is the simple instruction of how to execute the your groovy script:
 * <code>
 * SSHManager sshManager = new SSHManager("msidor", "mypassword", "127.0.0.1", null, 22, 60000,60000);
 * sshManager.connect();
 * sshManager.executeGroovyScript(null,"hello.groovy", "src/main/resources")
 * sshManager.close();
 * </code> 
 * 
 * Once your script is executed, you may interact with shell via "cmd" variable. 
 * See @see {@link Command} for more informations about how to interact with shell. 
 */
public class SSHManager
{
	/**
	 * Logger object from sl4j framework
	 */
	private Logger     logger = LoggerFactory.getLogger( SSHManager.class );
	
	/**
	 * SSH user name
	 */
	private String     userName;
	
	/**
	 * SSH user password
	 */
	private String     password;
	
	/**
	 * SSH connection IP
	 */
	private String     connectionIP;
	
	/**
	 * SSH connection port
	 */
	private int        connectionPort;
	
	/**
	 * SSH channel that handles the connection 
	 */
    private JSch       sSHChannel;	
    
    /**
     * SSH session
     */
	private Session    sSHSession;
	
	/**
	 * SSH connection timeout
	 */
	private int        conTimeOut;
	
	/**
	 * Command time out. See @see {@link Command} for more informations about what does it means exactly
	 */
	private int        cmdTimeOut;
	
	/**
	 * Terminal type
	 */
    private String     ptyType = null;
    
    /**
     * Terminal width, columns
     */
    private int         col=0;
    
    /**
     * Terminal height, rows
     */
    private int         row=0;
    
    /**
     * Terminal width, pixels
     */
    private int         wp=0;
    
    /**
     * Terminal height, pixel
     */
    private int         hp=0;



	/**
	 * Class constructor. The only way to instantiate the SSHManager manager. 
	 * 
	 * @param userName - SSH user name
	 * @param password - SSH user password
	 * @param connectionIP - SSH connection IP
	 * @param knownHostsFileName - file name with known hosts. If null, JSch to not use "StrictHostKeyChecking" (this introduces insecurities and should only be used for testing purposes)
	 * @param connectionPort - SSH connection port
	 * @param contimeOutMilliseconds - SSH connection timeout
	 * @param cmdTimeOutMilliseconds - Command time out. See @see {@link Command} for more informations about what does it means exactly
	 * @throws JSchException
	 */
	public SSHManager(String userName, String password, String connectionIP, String knownHostsFileName, int connectionPort, int contimeOutMilliseconds, int cmdTimeOutMilliseconds,String ptyType,int col,int row,int wp,int hp) throws JSchException
	{
        sSHChannel = new JSch();

        if (knownHostsFileName != null) 
            sSHChannel.setKnownHosts(knownHostsFileName);
        else 
            JSch.setConfig("StrictHostKeyChecking", "no");

        this.userName = userName;
        this.password = password;
        this.connectionIP = connectionIP;
        this.connectionPort = connectionPort;
        this.conTimeOut = contimeOutMilliseconds;
        this.cmdTimeOut = cmdTimeOutMilliseconds;

        this.ptyType = ptyType;
        this.col=col;
        this.row=row;
        this.hp=hp;       
        this.wp=wp;
        
	}

	/**
	 * Connect to SSH server
	 * @throws JSchException
	 */
	public void connect() throws JSchException
	{
		sSHSession = sSHChannel.getSession(userName, connectionIP, connectionPort);
		sSHSession.setPassword(password);
		sSHSession.connect(conTimeOut);
	}
	
	/**
	 * Execute groovy script via SSH connection. 
	 * 
	 * Once your script is executed, you may interact with shell via "cmd" variable. 
	 * See @see {@link Command} for more informations about how to interact with shell.
	 *
	 * This method calls executeGroovyScript("shell", Map<String, String> parameters,String mainScript, String... scriptFiles).
	 * See that method for more details.
	 * 
	 * @param parameters - map of objects that will be binded to the groovy execution under specified key
	 * @param mainScript - name of the groovy script to execute
	 * @param scriptFilesRoots - the groovy scripts roots
	 * @return true if script was executed successfully
	 */
	public boolean executeGroovyScript(Map<String, Object> parameters,String mainScript, String... scriptFilesRoots)
	{
	    return executeGroovyScript("shell", parameters, mainScript, scriptFilesRoots );
	}

	/**
	 * Execute groovy script via SSH connection.
	 * 
	 * Once your script is executed, you may interact with shell via "cmd" variable. 
	 * See @see {@link Command} for more informations about how to interact with shell.
	 * 
	 * @param channelName - name of JSCH channel
	 * @param parameters - map of objects that will be binded to the groovy execution under specified key
	 * @param mainScript - name of the groovy script to execute
	 * @param scriptFilesRoots - the groovy scripts roots
	 * @return true if script was executed successfully
	 */
	public boolean executeGroovyScript(String channelName, Map<String, Object> parameters,String mainScript, String... scriptFilesRoots)
	{
		//initiate channel
		Channel channel = null;
		
		//create the command execution object
		Command cmd = new Command();
		cmd.setOutputTimeout( cmdTimeOut );
		
		//get the output and input streams
        InputStream is = cmd.getInputStream();
        OutputStream os = cmd.getOutputStream();
		
        boolean result = false;
        
		try
		{
			//open the channel
			channel = sSHSession.openChannel(channelName);
			
			//set pty only if one has been given
			if(ptyType!=null)
			    ((ChannelShell)channel).setPtyType(ptyType);
			
			//set terminal size only if all has been defined
			if(col>0 && row>0 && wp>0 && hp>0)
			    ((ChannelShell)channel).setPtySize( col, row, wp, hp );
						
			channel.setInputStream(is);
			channel.setOutputStream(os);
			channel.connect(1000);
				
			//execute the groovy script
			GroovyScriptEngine gse = new GroovyScriptEngine(scriptFilesRoots);
			
			//bind the command object
			Binding binding = new Binding();
			binding.setVariable("cmd", cmd);
			
			//bind the optional parameters
			if(parameters!=null && parameters.size()>0)
			{
			    Set<String> keys = parameters.keySet();
			    for ( String key : keys )
			        binding.setVariable(key, parameters.get( key ));
			}
			
			//execute the script
			gse.run(mainScript, binding);	
			
			result = true;

		}
		catch (Exception e)
		{
		    logger.error("Exception while executing groovy script",e);

		}		
		finally
		{
			//gently disconnect 
			try
			{
				channel.disconnect();			
				is.close();
				os.close();
			}
			catch(Exception e)
			{
				logger.error(""+e.getMessage(),e);
			}
		}
		
		return result;

	}

	/**
	 * Close the connection
	 */
	public void close()
	{
		sSHSession.disconnect();
	}




}