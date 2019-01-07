/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.assembler;

/**
 * Supported options for building packages
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageOptions {

    /**
     * Specification key
     */
    String SPEC = "SPEC";

    /**
     * Supported compression
     */
    enum COMPRESSION {
        NONE,
        GZIP,
        BZIP2,
        ZIP
    }

    /**
     * Compression key
     */
    String COMPRESSION_KEY = "COMPRESSION";

    /**
     * Supported archive formats
     */
    enum ARCHIVE {
        NONE,
        TAR,
        ZIP
    }

    /**
     * Archive key
     */
    String ARCHIVE_KEY = "ARCHIVE";

    /**
     * Supported checksum algorithms
     */
    enum Algo {
        SHA512,
        SHA256,
        MD5
    }

    /**
     * Algorithm key
     */
    String ALGO_KEY = "ALGO";


}