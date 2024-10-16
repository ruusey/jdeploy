package com.jdeploy.service;

import java.io.File;
import java.time.Instant;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class DeploymentHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentHelper.class);

	private Session session = null;
	private Channel sftpChannel = null;
	private ChannelSftp channelSftp = null;

	private Channel execChannel = null;
	private ChannelExec channelExec = null;

	public DeploymentHelper(String remoteHost, Integer remotePort, String user, String password, String keyFile) {
		final JSch jsch = new JSch();
		try {
			if (keyFile != null) {
				jsch.addIdentity(keyFile);
			}
			this.session = jsch.getSession(user, remoteHost, remotePort);
			if (password != null) {
				this.session.setPassword(password);
			}
			final Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			this.session.setConfig(config);
			this.session.connect(60000);
			this.sftpChannel = this.session.openChannel("sftp");
			this.sftpChannel.connect();
			this.channelSftp = (ChannelSftp) this.sftpChannel;
		} catch (Exception e) {
			LOGGER.error("Failed to establish SSH session. Reason: {}", e);
		}
	}

	public void termiateSession() {
		try {
			this.sftpChannel.disconnect();
			this.channelSftp.disconnect();
			this.session.disconnect();
			this.channelExec.disconnect();
			this.execChannel.disconnect();
		} catch (Exception e) {
			LOGGER.error("Failed to clean up SSH resosurces. Reason: {}", e);
		}
	}

	// Exectutes two remote shell commands on the target box
	// One to kill the existing processs
	// Two to restart the new process
	public void doKillRun(String sourceFile, String redirectOut) {
		LOGGER.info("Performing kill and run of file {} with params {}", sourceFile, redirectOut);
		final String[] fileParts = extractFileParts(sourceFile);
		final String extraParams = redirectOut == null ? "" : "> " + fileParts[0] + "/" + redirectOut;

		try {
			final String[] commands = new String[] { "pkill -f " + fileParts[1],
					"nohup java -jar " + sourceFile + " " + extraParams + "&" };
			final long start = Instant.now().toEpochMilli();
			for (String command : commands) {
				this.doRun(command);
				Thread.sleep(500);
			}
			LOGGER.info("Executed kill and run for file {}  in {}ms", sourceFile,
					(Instant.now().toEpochMilli() - start));
		} catch (Exception e) {
			LOGGER.error("Failed to execute Kill and Run command. Reason: {}", e);
		}
	}

	public void doRun(String command) {
		LOGGER.info("Performing run of command {} ", command);
		try {
			final long start = Instant.now().toEpochMilli();
			this.execChannel = this.session.openChannel("exec");
			this.channelExec = ((ChannelExec) this.execChannel);
			LOGGER.info("Executing remote shell command '{}'", command);
			this.channelExec.setCommand(command);
			this.execChannel.connect();
			this.execChannel.disconnect();
			LOGGER.info("Executed run of command {}  in {}ms", command, (Instant.now().toEpochMilli() - start));
		} catch (Exception e) {
			LOGGER.error("Failed to execute Kill and Run command. Reason: {}", e);
		}
	}

	// Sftps the target file path to the remote file location
	public void doSftp(String sourceFile, String targetFile) {
		try {
			final File f = new File(sourceFile);
			if (!f.exists()) {
				throw new Exception("Source file " + sourceFile + " does not exist.");
			}
			// If the target file lies within a folder we need to CD to that folder
			if (targetFile.indexOf("/") > -1) {
				final String lastFolder = extractFileParts(targetFile)[0];
				this.channelSftp.cd(lastFolder);
			}
			LOGGER.info("Storing file as remote filename: {} under path {}", extractFileParts(targetFile)[1],
					extractFileParts(targetFile)[0]);
			final long transferStart = Instant.now().toEpochMilli();
			this.channelSftp.put(new java.io.FileInputStream(f), extractFileParts(targetFile)[1]);
			LOGGER.info("SFTP transfer successful in {}ms", (Instant.now().toEpochMilli() - transferStart));
		} catch (Exception e) {
			LOGGER.error("Storing remote file failed. Reason: {}", e);
		}
	}

	public void deployAndRun(String sourceFile, String targetFile) {
		try {

			final long start = Instant.now().toEpochMilli();
			this.doSftp(sourceFile, targetFile);
			this.doKillRun(targetFile, "service-log.out");
			LOGGER.info("Deployment of file {} to remote directory {} completed in {}ms",
					extractFileParts(sourceFile)[1], targetFile, (Instant.now().toEpochMilli() - start));
			this.termiateSession();
		} catch (Exception e) {
			LOGGER.error("Failed to deploy file {}. Reason: {}", sourceFile, e);
		}
	}

	public void deployAndExecuteCommand(String sourceFile, String targetFile, String commandToRun) {
		try {
			final long start = Instant.now().toEpochMilli();
			this.doSftp(sourceFile, targetFile);
			this.doRun(commandToRun);
			LOGGER.info("Deployment of file {} to remote directory {} completed in {}ms",
					extractFileParts(sourceFile)[1], targetFile, (Instant.now().toEpochMilli() - start));
			this.termiateSession();
		} catch (Exception e) {
			LOGGER.error("Failed to deploy file {}. Reason: {}", sourceFile, e);
		}
	}

	// Returns an array of [directoriesFileIsIn, fileName]
	public static String[] extractFileParts(String filePath) {
		final int lastSlash = filePath.lastIndexOf("/");
		if (lastSlash == -1) {
			return new String[] { "", filePath };
		}
		final String directory = filePath.substring(0, lastSlash);
		final String file = filePath.substring(lastSlash + 1);
		return new String[] { directory, file };
	}

	public static void main(String[] args) {
		final CommandLineParser parser = new DefaultParser();
		final Options options = new Options();
		options.addOption("h", "remote-host", true, "Remote host");
		options.addOption("p", "remote-port", true, "Remote port (usually 22)");
		options.addOption("u", "remote-username", true, "Remote login username");
		options.addOption("y", "remote-user-password", true, "Remote login passsword");
		options.addOption("i", "remote-private-key", true, "Remote host privvate key");
		options.addOption("s", "source-file", true, "Local file path to deploy");
		options.addOption("t", "target-file", true, "Remote file path to deploy to");

		if ((args.length > 0) && args[0].contains("spring.output.ansi")) {
			args[0] = "";
		}
		try {
			final CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("help")) {
				final HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("DeploymentHelper v0.1", options, true);
				System.exit(-1);
			}
			final String host = cmd.getOptionValue("remote-host");
			final String port = cmd.getOptionValue("remote-port");

			final String user = cmd.getOptionValue("remote-username");
			final String password = cmd.getOptionValue("remote-user-password");
			final String key = cmd.getOptionValue("remote-private-key");

			final String sourceFile = cmd.getOptionValue("source-file");
			final String targetFile = cmd.getOptionValue("target-file");

			final DeploymentHelper helper = new DeploymentHelper(host, Integer.parseInt(port), user, password, key);
			helper.deployAndRun(sourceFile, targetFile);
			
			//helper.deployAndExecuteCommand(sourceFile, targetFile, "/opt/UPS/apiservice/deployment/script/deployPortalClaimService.sh");
		} catch (Exception e) {
			LOGGER.error("Failed to parse command line arguments. Reason: {}", e);
		}
	}
}
