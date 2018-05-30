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
package org.dataconservancy.pass.deposit.messaging.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;

/**
 * Maps a {@code RepositoryCopy} {@link RepositoryCopy.CopyStatus copy status} to a {@link Deposit.DepositStatus
 * deposit status}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class RepositoryCopyStatusMapper extends AbstractStatusMapper<RepositoryCopy.CopyStatus> {

    public RepositoryCopyStatusMapper(JsonNode statusMap) {
        super(statusMap);
    }

    @Override
    protected String getConfigurationKey() {
        return REPO_COPY_MAPPING_KEY;
    }

    /**
     * Maps a {@code RepositoryCopy} {@link RepositoryCopy.CopyStatus copy status} to a {@link Deposit.DepositStatus
     * deposit status}.
     *
     * @param statusToMap the {@code RepositoryCopy
     * @return the mapped {@code DepositStatus}
     */
    public Deposit.DepositStatus map(RepositoryCopy.CopyStatus statusToMap) {
        if (statusToMap == null) {
            return null;
        }
        return mapInternal(statusToMap.name());
    }
}
