package com.easleydp.tempctrl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsCommandExecuter {
    private static final Logger logger = LoggerFactory.getLogger(OsCommandExecuter.class);

    /**
     * Execute some OS command, as if from the command line.
     * 
     * Uses {@link int#java.lang.Process.waitFor() Process.waitFor()}, which:
     * > Causes the current thread to wait, if necessary, until the
     * process represented by this {@code Process} object has
     * terminated. This method returns immediately if the process
     * has already terminated. If the process has not yet
     * terminated, the calling thread will be blocked until the
     * process exits.
     * 
     * @param command a string array containing the program and its arguments
     * 
     * @return The stdout resulting from the executed command if successful,
     *         otherwise null (in which case some diagnostic will be logged at Error
     *         level).
     */
    public static String execute(String... command) {
        return innerExecute(true, command);
    }

    /**
     * As {@link String#com.easleydp.tempctrl.util.execute(String... command)
     * execute(String... command)} but a non-zero exit code is not regarded as
     * as error (a warning is logged instead of an error). The distinction is
     * important in the tempctrl app since errors lead to email notifications.
     * 
     * Intended for commands such as `ping` in cases where 'host not alive' is not
     * necessarily an error.
     */
    public static String executeWithNoExitCodeError(String... command) {
        return innerExecute(false, command);
    }

    private static String innerExecute(boolean exitCodeIsError, String... command) {
        try {
            Process process = new ProcessBuilder()
                    .command(command)
                    .directory(new File(System.getProperty("user.home")))
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (exitCodeIsError) {
                    logger.error("Non-zero exitCode executing {}: {}", command[0], exitCode);
                } else {
                    logger.warn("Non-zero exitCode executing {}: {}", command[0], exitCode);
                }
                return null;
            }
            if (process.getErrorStream().read() != -1) {
                logger.error("Error executing {}: {}", command[0], readInputStream(process.getErrorStream()));
                return null;
            }
            return readInputStream(process.getInputStream());
        } catch (IOException e) {
            logger.error("IOException executing " + command[0], e);
            return null;
        } catch (InterruptedException e) {
            logger.error("InterruptedException executing " + command[0], e);
            return null;
        }
    }

    private static String readInputStream(InputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String line;
        while ((line = in.readLine()) != null) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append(line);
        }
        in.close();
        return sb.toString().trim();
    }
}
