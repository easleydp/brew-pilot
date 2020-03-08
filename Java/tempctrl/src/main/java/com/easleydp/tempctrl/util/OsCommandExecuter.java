package com.easleydp.tempctrl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsCommandExecuter
{
    private static final Logger logger = LoggerFactory.getLogger(OsCommandExecuter.class);

    public static String execute(String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(command);
            builder.directory(new File(System.getProperty("user.home")));
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("Non-zero exitCode executing " + command[0] + ": " + exitCode);
                return null;
            }
            if (process.getErrorStream().read() != -1) {
                logger.error("Error executing " + command[0] + ": " + readInputStream(process.getErrorStream()));
                return null;
            }
            return readInputStream(process.getInputStream());
        }
        catch (IOException e) {
            logger.error("IOException executing " + command[0], e);
            return null;
        }
        catch (InterruptedException e) {
            logger.error("InterruptedException executing " + command[0], e);
            return null;
        }
    }

    private static String readInputStream(InputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String line;
        while((line = in.readLine()) != null ) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append(line);
        }
        in.close();
        return sb.toString().trim();
    }
}
