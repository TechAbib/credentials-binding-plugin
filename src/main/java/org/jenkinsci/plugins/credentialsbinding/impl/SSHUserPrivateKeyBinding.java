/*
 * The MIT License
 *
 * Copyright 2013 jglick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.credentialsbinding.impl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.Secret;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class SSHUserPrivateKeyBinding extends MultiBinding<SSHUserPrivateKey> {

    public final String keyFileVariable;
    public final String usernameVariable;
    public final String passphraseVariable;

    @DataBoundConstructor public SSHUserPrivateKeyBinding(String keyFileVariable, String passphraseVariable, String usernameVariable, String credentialsId) {
        super(credentialsId);
        this.keyFileVariable = keyFileVariable;
        this.usernameVariable = usernameVariable;
        this.passphraseVariable = passphraseVariable;
    }

    @Override protected Class<SSHUserPrivateKey> type() {
        return SSHUserPrivateKey.class;
    }

    @Override public Set<String> variables() {
        return ImmutableSet.of(keyFileVariable, usernameVariable, passphraseVariable);
    }

    @Override public MultiEnvironment bind(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        SSHUserPrivateKey sshKey = getCredentials(build);
        FilePath keyFile =  tempDir(workspace).child("ssh-key-" + keyFileVariable);

        StringWriter stringWriter = new StringWriter();
        PrintWriter keysFileStream = new PrintWriter(stringWriter);

        for (String key : sshKey.getPrivateKeys()) {
            keysFileStream.println(key);
        }

        keysFileStream.close();
        keyFile.write(stringWriter.toString(), "UTF-8");
        keyFile.chmod(0400);

        Map<String, String> map = new HashMap<String, String>();
        map.put(keyFileVariable, keyFile.getRemote());
        Secret passphrase = sshKey.getPassphrase();
        if (passphrase != null) {
            map.put(passphraseVariable, passphrase.getPlainText());
        } else {
            map.put(passphraseVariable, "");
        }
        map.put(usernameVariable, sshKey.getUsername());

        return new MultiEnvironment(map, new KeyRemover(keyFile.getRemote()));
    }

    private static class KeyRemover implements MultiBinding.Unbinder {

        private static final long serialVersionUID = 1;

        private final String filePath;

        KeyRemover(String filePath) {
            this.filePath = filePath;
        }

        @Override public void unbind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            workspace.child(this.filePath).delete();
        }

    }

    // TODO 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    @Extension public static class DescriptorImpl extends BindingDescriptor<SSHUserPrivateKey> {

        @Override protected Class<SSHUserPrivateKey> type() {
            return SSHUserPrivateKey.class;
        }

        @Override public String getDisplayName() {
            return Messages.SSHUserPrivateKeyBinding_ssh_user_private_key();
        }

    }

}
