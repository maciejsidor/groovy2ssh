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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Maciej SIDOR
 *
 * <p>
 * This class allows to interact with shell via SSH connection.
 * It is available in your groovy script as the "cmd" variable.
 * </p>
 * 
 * <p>
 * Here are some examples of how you might use it in order to interact with shell through SSH connection:
 * </p>
 * 
 * <h1>Example 1</h1>
 * In this example the script is processing character by character. 
 * The script is blocked each time the readBuffer() is called until a single character is available in the command buffer. 
 * <code>
 * outputLine = '';
 * while ((outputLine = cmd.readBuffer()) != '$')
 * {
 *     print outputLine;
 * }
 * print outputLine;
 * 
 * is.sendCommand("ls\n");
 * 
 * while ((outputLine = cmd.readBuffer()) != '$')
 * {
 *     print outputLine;
 * }
 * print outputLine;
 * 
 </code>
 * <h1>Example 2</h1>
 * In this example the script is processing line by line. 
 * The script is blocked each time the readBuffer() is called until a full line is available in the command buffer or until no updates were made to the command buffer since the timeout specified in outputTimeOut.
 * In the second case the unaccomplished line is returned. 
 <code>
 * 
 * outputLine = "";
 * outputCheck = false;
 * while ((outputLine = cmd.readBufferString()) != null)
 * {
 *     print outputLine;
 *     
 *     if(outputLine.endsWith("\$ "))
 *         outputCheck = true; 
 * }
 * 
 * if(outputCheck)
 *     cmd.sendCommand("ls\n");
 * else
 *     throw new Exception ("Could not execute LS");
 *     
 * outputLine = "";
 * while ((outputLine = cmd.readBufferString()) != null)
 * {
 *     print outputLine;
 * }
 * 
 </code>
 * <h1>Example 3</h1>
 * <p>
 * Yet another example of line by line processing.
 * This time, however, there is no timeout handled by Command API.
 * If no complete line is available, the readBufferLine() returns whatever is there is the command buffer.
 * Contrary to readBufferString(), the readBufferLine() erase returned line from the buffer only if full line was returned.
 * </p>
 * 
 * <p>See the following scenario to understand better how it works:
 * <or>
 * <li>SSH appends the buffer with "/local/msidor #"</li>
 * <li>Call of readBufferLine() returns "/local/msidor #"</li>
 * <li>SSH appends the buffer with " ls"</li> 
 * <li>Call of readBufferLine() returns "/local/msidor # ls"</li>
 * <li>SSH appends the buffer with "\n"</li> 
 * <li>Call of readBufferLine() returns "/local/msidor # ls\n"</li>
 * <li>SSH appends the buffer with "foo.txt\n"</li> 
 * <li>Call of readBufferLine() returns "foo.txt\n"</li>
 * <li>SSH appends the buffer with "/local/msidor #"</li>
 * <li>Call of readBufferLine() returns "/local/msidor #"</li>
 * </or>
 * </p>
 * 
 *  <p>This way you may develop far much faster scripts but you have to be careful though. For instance you have to implement the timeouts by yourself.</p> 
 *
 <code>
 * 
 * outputCheck = false;
 * lastUpdate=System.currentTimeMillis();
 * while (System.currentTimeMillis()-lastUpdate<15000 && !outputCheck)
 * {
 * 
 *     if((outputLine = cmd.readBufferLine()) != null)
 *     {
 *         if(outputLine.endsWith("\n"))
 *             print outputLine;
 *         
 *         if(outputLine.endsWith("\$ "))
 *             outputCheck = true;
 *     }   
 * }
 * 
 * if(outputCheck)
 *     cmd.sendCommand("ls\n");
 * else
 *     throw new Exception ("Could not execute LS");           
 *     
 * outputCheck = false;    
 * while (System.currentTimeMillis()-lastUpdate<15000 && !outputCheck)
 * {
 * 
 *     if((outputLine = cmd.readBufferLine()) != null)
 *     {
 *         if(outputLine.endsWith("\n"))
 *             print outputLine;
 *         
 *         if(outputLine.endsWith("\$ "))
 *             outputCheck = true;
 *     }   
 * }             
 *  </code> 
 */
public class Command
{
    private ByteArrayInputStream    commandToSend             	= null;
    private StringBuffer            commandOutput               = new StringBuffer();

    private boolean                 isCommandSent               = true;
    private boolean                 isClosed                    = false;
    private long                    lastUpdate                  = -1;

    private OutputStream            cmdOutputStream             = null;
    private InputStream             cmdInputStream              = null;
    
    private Logger                  logger                      = LoggerFactory.getLogger( Command.class );
   
    private int                     outputTimeOut               = 2000;
    
    /**
     * Default constructor of Command class.
     */
    public Command()
    {
    	/*
    	 * Create the OutputStream that will write to this object string buffer.
    	 */
        cmdOutputStream = new OutputStream()
        {
            
            @Override
            /**
             * Default write implementation
             */            
            public void write(int b) throws IOException
            {
                commandOutput.append((char)b);
                
                //set the last update to current time
                setLastUpdate(System.currentTimeMillis());
            }
        };
        
        /*
         * Create the InputStream that will read from this object's command
         */
        cmdInputStream = new InputStream()
        {
            
            @Override
            /**
             * Default read implementation
             */
            public int read() throws IOException
            {
                int result = 0;
                //read while command object is not closed
                while(!isClosed())
                {
                                
                	//if command is set but not sent yet 
                    if(getCommand()!=null && !isCommandSent())
                    {
                    	//read the byte from command
                        result = getCommand().read();  
                        
                        /*
                         * If the end of command is reached, set the "sent command" flag to true.
                         * This is done in order to prevent the JSCH Channel of closing the input.
                         * It seems that the first -1 tells to JSCH that this is the end of command whereas the second -1 kills the channel input stream. 
                         */                        
                        if(result==-1)
                            setCommandSent(true);
                        
                        return result;
                    }
                    
                }      
                
                //this will kill the input stream on JSCH side as the first -1 should have been already sent.
                return -1;
           }
            
            
            @Override
            /**
             * Default close implementation
             */
            public void close() throws IOException
            {
                super.close();
                setClosed(true);
                
            }
        };
    }
    
    /**
     * Allows to log the output to the class logger as info message
     * @param output to log to the logger
     */
    public void logOutput(String output)
    {
        logger.info( output );
    }
    
    /**
     * Sends command to the shell. This instruction will block until the previous command is not sent.
     * @param cmd - command to be sent to shell
     * @return true if command was successfully set
     */
    public boolean sendCommand(String cmd)
    {
        try
        {
        	//wait until the previous command is not sent completely
            while(!isCommandSent());
                        
            synchronized(this)
            {
            	//set the new command
                commandToSend = new ByteArrayInputStream(cmd.getBytes("UTF-8"));
                
                //reset the flag 
                isCommandSent = false;
            }
            
            return true;
        }
        catch ( UnsupportedEncodingException e )
        {
            //exception to ignore
            return false;
        }
    }
    
    /**
     * Reads the line from the buffer. 
     * There are two possible cases: 
     * <or>
     * <li>returns the line from the buffer immediately if the full line is available</li>
     * <li>returns anything what is in the buffer</li>
     * </or>
     * This command will not block. 
     * The main difference between this method and readBufferString() is the fact that returned content is erased from the buffer only if full line was returned.
     * See the following scenario to understand better how it works:
     * <or>
     * <li>SSH appends the buffer with "/local/msidor #"</li>
     * <li>Call of readBufferLine() returns "/local/msidor #"</li>
     * <li>SSH appends the buffer with " ls"</li> 
     * <li>Call of readBufferLine() returns "/local/msidor # ls"</li>
     * <li>SSH appends the buffer with "\n"</li> 
     * <li>Call of readBufferLine() returns "/local/msidor # ls\n"</li>
     * <li>SSH appends the buffer with "foo.txt\n"</li> 
     * <li>Call of readBufferLine() returns "foo.txt\n"</li>
     * <li>SSH appends the buffer with "/local/msidor #"</li>
     * <li>Call of readBufferLine() returns "/local/msidor #"</li>
     * </or>
     * @return <or>
     * <li>the line from the buffer immediately if the full line is available</li>
     * <li>null if nothing is no complete line is yet available</li>
     * </or>
     */
    public String readBufferLine()
    {
        
        //wait until the full line is available or until the the buffer is not updated for more than the value specified in commandOutputTimeOut.
        int index = commandOutput.indexOf("\n");
        
        //return if anything was found
        if(index>=0)
        {
            index++;
            String result = commandOutput.substring(0,index);
            commandOutput.delete(0,index);
            return result;  
        }
        else
        {
            index = commandOutput.length();
            if(index>0)
            {
                String result = commandOutput.substring(0,index);            
                return result;
            }
        }
        
        return null;
                
    }    
    
    
    /**
     * Calls readBufferString with default outputTimeOut
     * @return result of readBufferString with default outputTimeOut
     */
    public String readBufferString()
    {
        return readBufferString(outputTimeOut);
    }
    
    /**
     * Reads the line from the buffer. 
     * There are two possible cases: 
     * <or>
     * <li>returns the line from the buffer immediately if the full line is available</li>
     * <li>returns anything what is in the buffer after the buffer was not updated for more than the value specified in commandOutputTimeOut</li>
     * </or>
     * That means that the command may block until the full line is available or until the the buffer is not updated for more than the value specified in commandOutputTimeOut. 
     * The returned content is erased from the buffer.
     * 
     * @param commandOutputTimeOut - the max timeout in milliseconds for which the command may block if no full line is present in buffer and no updates were made.
     * Set the negative value in order to disable the timeOut.
     * @return <or>
     * <li>the line from the buffer immediately if the full line is available</li>
     * <li>anything what is in the buffer after the buffer was not updated for more than the value specified in commandOutputTimeOut</li>
     * </or>
     */
    public String readBufferString(int commandOutputTimeOut)
    {
    	//if no update to buffer were made since last null-return method execution, the timeout will count since now. 
        if(getLastUpdate()==-1)
            setLastUpdate(System.currentTimeMillis());
        
        //wait until the full line is available or until the the buffer is not updated for more than the value specified in commandOutputTimeOut.
        int index = 0;        
        while((index = commandOutput.indexOf("\n"))<0 && (System.currentTimeMillis()-getLastUpdate()<commandOutputTimeOut || commandOutputTimeOut<0)  );
        
        if(index<0 && commandOutput.length()>0)
            index = commandOutput.length();
        else if(index>=0)
            index++;
        
        //return if anything was found
        if(index>0)
        {
            String result = commandOutput.substring(0,index);
            commandOutput.delete(0,index);
            return result;  
        }

        //set the last update to -1 if nothing was returned. This will add some timeout for next time this method is called due to give some time for output to be written.
        setLastUpdate(-1);
        
        return null;
    }
    
    /**
     * Calls readBufferChar with default outputTimeOut
     * @return result of readBufferChar with default outputTimeOut
     */
    public char readBufferChar()
    {
        return readBufferChar(outputTimeOut);
    }    
    
    /**
     * Reads the character from the buffer is one is available. 
     * If not, this method will block until a character is available or until the timeOut specified in commandOutputTimeOut variable. 
     * The returned content is erased from the buffer.
     * 
     * @param commandOutputTimeOut timeOut for which this method will block. Set negative value in order to disable the timeout.
     * @return character from the buffer.
     */
    public char readBufferChar(int commandOutputTimeOut)
    {
        while(commandOutput.length()==0 && (System.currentTimeMillis()-getLastUpdate()<commandOutputTimeOut || commandOutputTimeOut<0));
        
        if(commandOutput.length()>0)
        {
            char result = commandOutput.charAt(0);
            commandOutput.deleteCharAt(0);
            return result;
        }
        return '\0';
    }
    
    /**
     * Returns last buffer update time
     * @return last buffer update time
     */
    public synchronized long getLastUpdate()
    {
        return lastUpdate;
    }

    /**
     * Sets the last buffer update time
     * @param lastUpdate - last buffer update time
     */
    public synchronized void setLastUpdate( long lastUpdate )
    {
        this.lastUpdate = lastUpdate;
    }
        
    /**
     * Returns true if command was completely send
     * @return true if command was completely send
     */
    public synchronized boolean isCommandSent()
    {
        return isCommandSent;
    }
    
    /**
     * Sets the command sending status
     * @param isCommandSent - command sending status
     */
    private synchronized void setCommandSent(boolean isCommandSent)
    {
        this.isCommandSent=isCommandSent;
    }
    
    /**
     * Returns the command
     * @return the command
     */
    private synchronized ByteArrayInputStream getCommand()
    {
        return commandToSend;
    }    
    
    /**
     * Returns true if the command object was closed
     * @return true if the command object was closed
     */
    public synchronized boolean isClosed()
    {
        return isClosed;
    }
    
    /**
     * Sets the command object was closed flag
     * @param isClosed true if the command object was closed
     */
    private synchronized void setClosed(boolean isClosed)
    {
        this.isClosed=isClosed;
    }

    
    /** GETTERS and SETTERS **/
    
    /**
     * Returns the OutputStream of this command object to be used as channel OutputStream
     * @return the OutputStream of this command object to be used as channel OutputStream
     */
    protected OutputStream getOutputStream()
    {
        return cmdOutputStream;
    }
    
    /**
     * Returns the InputStream of this command object to be used as channel InputStream
     * @return the InputStream of this command object to be used as channel InputStream
     */
    protected InputStream getInputStream()
    {
        return cmdInputStream;
    }        

    /**
     * Returns default output timeOut
     * @return default output timeOut
     */
    public int getOutputTimeout()
    {
        return outputTimeOut;
    }

    /**
     * Sets the default output timeOut
     * @param default output timeOut
     */
    public void setOutputTimeout( int outputTimeout )
    {
        this.outputTimeOut = outputTimeout;
    }    
}
