/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.token.basicauth;

import io.micronaut.core.util.Toggleable;

/**
 * Configuration for the {@link BasicAuthTokenReader}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface BasicAuthTokenReaderConfiguration extends Toggleable {

    /**
     *
     * @return a boolean flag indicating whether BasicAuthTokenReader bean should be enabled or not
     */
    boolean isEnabled();

    /**
     *
     * @return a Prefix before the token in the header value. E.g. Basic
     */
    String getPrefix();

    /**
     *
     * @return an HTTP Header name. e.g. Authorization
     */
    String getHeaderName();
}
