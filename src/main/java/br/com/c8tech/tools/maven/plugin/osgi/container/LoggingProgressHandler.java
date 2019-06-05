/**
 * ==========================================================================
 * Copyright © 2015-2019 Cristiano Gavião, C8 Technology ME.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cristiano Gavião (cvgaviao@c8tech.com.br)- initial API and implementation
 * ==========================================================================
 */
package br.com.c8tech.tools.maven.plugin.osgi.container;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ProgressMessage;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.plugin.logging.Log;

class LoggingProgressHandler implements ProgressHandler {

  private static final Splitter LINE_SPLITTER = Splitter.on('\n');
  private final Log log;
  private final boolean verbose;
  private String builtImageId;
  private Map<String, String> imageStatuses = new HashMap<>();

  LoggingProgressHandler(Log log, boolean verbose) {
    this.log = log;
    this.verbose = verbose;
  }

  public static LoggingProgressHandler forLog(Log log, boolean verbose) {
    return new LoggingProgressHandler(log, verbose);
  }

  @Nullable
  public String builtImageId() {
    return builtImageId;
  }

  @Override
  public void progress(ProgressMessage message) throws DockerException {
    if (message.error() != null) {
      handleError(message.error());
    } else if (message.progressDetail() != null) {
      handleProgress(message.id(), message.status(), message.progress());
    } else if ((message.status() != null) || (message.stream() != null)) {
      handleGeneric(message.stream(), message.status());
    }

    String imageId = message.buildImageId();
    if (imageId != null) {
      builtImageId = imageId;
    }
  }

  void handleGeneric(@Nullable String stream, @Nonnull String status) {
    final String value;
    if (stream != null) {
      value = trimNewline(stream);
    } else {
      value = status;
    }
    for (String line : LINE_SPLITTER.split(value)) {
      log.info(line);
    }
  }

  void handleProgress(@Nonnull String id, @Nonnull String status, @Nullable String progress) {
    if (verbose) {
      if (progress == null) {
        log.info(MessageFormat.format("Image {0}: {1}", id, status));
      } else {
        log.info(MessageFormat.format("Image {0}: {1} {2}", id, status, progress));
      }
    } else {
      if (!Objects.equal(imageStatuses.get(id), status)) {
        imageStatuses.put(id, status);
        log.info(MessageFormat.format("Image {0}: {1}", id, status));
      }
    }
  }

  void handleError(@Nonnull String error) throws DockerException {
    log.error(error);
    throw new DockerException(error);
  }

  @Nonnull
  static String trimNewline(@Nonnull String string) {
    if (string.endsWith("\n")) {
      return string.substring(0, string.length() - 1);
    } else {
      return string;
    }
  }
}
