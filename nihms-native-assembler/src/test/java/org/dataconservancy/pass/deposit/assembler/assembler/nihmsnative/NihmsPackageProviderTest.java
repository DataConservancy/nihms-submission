/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.junit.Test;

import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsPackageProvider.getNonCollidingFilename;
import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsPackageProviderTest {

    @Test
    public void nonCollidingFilename() throws Exception {
        String nameIn, nameOut;

        nameIn = "test.txt";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertTrue("Non-colliding name was changed.", nameIn.contentEquals(nameOut));

        nameIn = "manifest.txt";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse("Colliding manifest name was not changed.", nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse("Colliding metadata name was not changed.", nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = getNonCollidingFilename(nameIn, DepositFileType.bulksub_meta_xml);
        assertTrue("Actual metadata name was changed.", nameIn.contentEquals(nameOut));
    }

}