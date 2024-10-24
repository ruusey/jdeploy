package com.jdeploy.service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import com.jcraft.jsch.UserInfo;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class DeploymentHelper {
	private Session session = null;
	private Channel sftpChannel = null;
	private Channel execChannel = null;
	private Channel shellChannel = null;

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
	        config.put("PreferredAuthentications", "publickey,gssapi-keyex,gssapi-with-mic,keyboard-interactive");
	        config.put("UseDNS", "no");
	        final UserInfo ui= new AuhenticatedUser();
	        this.session.setUserInfo(ui);
			this.session.setConfig(config);
			this.session.connect(15000);
			this.sftpChannel = this.session.openChannel("sftp");
			this.sftpChannel.connect();
			
			this.shellChannel = this.session.openChannel("shell");
			this.shellChannel.connect();
			
			
			this.execChannel = this.session.openChannel("exec");
			this.execChannel.connect();

		} catch (Exception e) {
			log.error("Failed to establish SSH session. Reason: {}", e);
		}
	}
	
	public ChannelSftp getSftpChannel() {
		return ((ChannelSftp) this.sftpChannel);
	}
	
	public ChannelExec getExecChannel() {
		return ((ChannelExec) this.execChannel);
	}
	
	public ChannelShell getShellChannel() {
		return ((ChannelShell) this.shellChannel);
	}

	public void termiateSession() {
		try {
			this.sftpChannel.disconnect();
			this.session.disconnect();
			this.execChannel.disconnect();
		} catch (Exception e) {
			log.error("Failed to clean up SSH resosurces. Reason: {}", e);
		}
	}
	
	public String readShell() {
		String shellContent = null;
		try {
			InputStream input = this.shellChannel.getInputStream();
			int size = input.available();
			if(size>0) {
				byte[] buffer = new byte[input.available()];
				shellContent = new String(buffer, StandardCharsets.UTF_8);
			}
			
		}catch(Exception e) {
			log.error("Failed to read remote shell. Reason: {}", e);
		}
		return shellContent;
	}
	
	public String readExec() {
		String shellContent = null;
		try {
			final InputStream input = this.getExecChannel().getInputStream();
			final int size = input.available();
			if(size>0) {
				final byte[] buffer = new byte[input.available()];
				shellContent = new String(buffer, StandardCharsets.UTF_8);
			}
			
		}catch(Exception e) {
			log.error("Failed to read remote shell. Reason: {}", e);
		}
		return shellContent;
	}
	
	public void writeShell(String command) {
		try {
			final OutputStream input = this.shellChannel.getOutputStream();
			final PrintWriter writer = new PrintWriter(input);
			writer.write(command);
			writer.flush();
			//writer.close();
		}catch(Exception e) {
			log.error("Failed to write remote shell. Reason: {}", e);
		}
	}

	// Exectutes two remote shell commands on the target box
	// One to kill the existing processs
	// Two to restart the new process
	public void doKillRun(String sourceFile, String redirectOut) {
		log.info("Performing kill and run of file {} with params {}", sourceFile, redirectOut);
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
			log.info("Executed kill and run for file {}  in {}ms", sourceFile,
					(Instant.now().toEpochMilli() - start));
		} catch (Exception e) {
			log.error("Failed to execute Kill and Run command. Reason: {}", e);
		}
	}

	public void doRun(String command) {
		log.info("Performing run of command {} ", command);
		try {
			final long start = Instant.now().toEpochMilli();
			this.execChannel = this.session.openChannel("exec");
			log.info("Executing remote shell command '{}'", command);
			this.getExecChannel().setCommand(command);
			this.execChannel.connect();
			this.execChannel.disconnect();
			log.info("Executed run of command {}  in {}ms", command, (Instant.now().toEpochMilli() - start));
		} catch (Exception e) {
			log.error("Failed to execute Kill and Run command. Reason: {}", e);
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
				this.getSftpChannel().cd(lastFolder);
			}
			log.info("Storing file as remote filename: {} under path {}", extractFileParts(targetFile)[1],
					extractFileParts(targetFile)[0]);
			final long transferStart = Instant.now().toEpochMilli();
			this.getSftpChannel().put(new java.io.FileInputStream(f), extractFileParts(targetFile)[1]);
			log.info("SFTP transfer successful in {}ms", (Instant.now().toEpochMilli() - transferStart));
		} catch (Exception e) {
			log.error("Storing remote file failed. Reason: {}", e);
		}
	}

	public void deployAndRun(String sourceFile, String targetFile) {
		try {

			final long start = Instant.now().toEpochMilli();
			this.doSftp(sourceFile, targetFile);
			this.doKillRun(targetFile, "service-log.out");
			log.info("Deployment of file {} to remote directory {} completed in {}ms",
					extractFileParts(sourceFile)[1], targetFile, (Instant.now().toEpochMilli() - start));
			this.termiateSession();
		} catch (Exception e) {
			log.error("Failed to deploy file {}. Reason: {}", sourceFile, e);
		}
	}

	public void deployAndExecuteCommand(String sourceFile, String targetFile, String commandToRun) {
		try {
			final long start = Instant.now().toEpochMilli();
			this.doSftp(sourceFile, targetFile);
			this.doRun(commandToRun);
			log.info("Deployment of file {} to remote directory {} completed in {}ms",
					extractFileParts(sourceFile)[1], targetFile, (Instant.now().toEpochMilli() - start));
			this.termiateSession();
		} catch (Exception e) {
			log.error("Failed to deploy file {}. Reason: {}", sourceFile, e);
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
			helper.doSftp(sourceFile ,targetFile);
			helper.doRun("chmod 777 "+targetFile);
			System.out.println("NEXT STEPS:");
			System.out.println("su upscapi");
			System.out.println("/opt/UPS/portalservice/script/deploy{Service}.sh");
			System.out.println("/opt/UPS/apiservice/script/deploy{Service}.sh");
			helper.termiateSession();
		} catch (Exception e) {
			log.error("Failed to parse command line arguments. Reason: {}", e);
		}
	}
}
