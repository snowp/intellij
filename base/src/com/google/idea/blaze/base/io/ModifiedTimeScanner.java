/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.io;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import java.io.File;

/** Reads the last modified times from a list of files. */
public class ModifiedTimeScanner {

  private static final class TimestampReader implements FileAttributeScanner.AttributeReader<Long> {

    private final FileAttributeProvider attributeProvider;

    TimestampReader(FileAttributeProvider attributeProvider) {
      this.attributeProvider = attributeProvider;
    }

    @Override
    public Long getAttribute(File file) {
      return attributeProvider.getFileModifiedTime(file);
    }

    @Override
    public boolean isValid(Long timestamp) {
      return timestamp != 0;
    }
  }

  public static ImmutableMap<File, Long> readTimestamps(Iterable<File> fileList) throws Exception {
    final TimestampReader timestampReader =
        new TimestampReader(FileAttributeProvider.getInstance());
    return FileAttributeScanner.readAttributes(
        fileList, timestampReader, BlazeExecutor.getInstance());
  }
}
