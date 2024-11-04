package com.jdeploy.service;

import java.awt.*;
import javax.swing.*;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class AuhenticatedUser implements UserInfo, UIKeyboardInteractive {
    private final static GridBagConstraints LAYOUT = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0), 0, 0);
    private String passwd;
    private JTextField passwordField = (JTextField) new JPasswordField(20);

    private Container panel;

    public AuhenticatedUser() {
        log.info("Created new SSH auth context");
    }

    public String getPassword() {
        return passwd;
    }

    public boolean promptYesNo(String str) {
        final Object[] options = { "yes", "no" };
        final int foo = JOptionPane.showOptionDialog(null, str, "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
                options[0]);
        return foo == 0;
    }

    public boolean promptPassword(String message) {
        final Object[] ob = { passwordField };
        final int result = JOptionPane.showConfirmDialog(null, ob, message, JOptionPane.OK_CANCEL_OPTION);
        log.info("Prompting user for RSA password");
        if (result == JOptionPane.OK_OPTION) {
            this.passwd = this.passwordField.getText();
            return true;
        } else {
            return false;
        }
    }

    public void showMessage(String message) {
        JOptionPane.showMessageDialog(null, message);
    }

    public String getPassphrase() {
        return null;
    }

    public boolean promptPassphrase(String message) {
        return false;
    }

    public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {

        this.panel = new JPanel();
        this.panel.setLayout(new GridBagLayout());

        LAYOUT.weightx = 1.0;
        LAYOUT.gridwidth = GridBagConstraints.REMAINDER;
        LAYOUT.gridx = 0;
        this.panel.add(new JLabel(instruction), LAYOUT);
        LAYOUT.gridy++;

        LAYOUT.gridwidth = GridBagConstraints.RELATIVE;

        final JTextField[] texts = new JTextField[prompt.length];
        for (int i = 0; i < prompt.length; i++) {
            LAYOUT.fill = GridBagConstraints.NONE;
            LAYOUT.gridx = 0;
            LAYOUT.weightx = 1;
            this.panel.add(new JLabel(prompt[i]), LAYOUT);

            LAYOUT.gridx = 1;
            LAYOUT.fill = GridBagConstraints.HORIZONTAL;
            LAYOUT.weighty = 1;
            if (echo[i]) {
                texts[i] = new JTextField(20);
            } else {
                texts[i] = new JPasswordField(20);
            }
            this.panel.add(texts[i], LAYOUT);
            LAYOUT.gridy++;
        }

        if (JOptionPane.showConfirmDialog(null, this.panel, destination + ": " + name, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
            final String[] response = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) {
                response[i] = texts[i].getText();
            }
            return response;
        } else {
            return null;
        }
    }
}