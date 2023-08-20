/**
 *
 * Copyright 2015 Kamran Zafar
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

package org.homio.app.extloader.jcl.exception;

import org.homio.app.extloader.jcl.ResourceType;

/**
 * @author Kamran Zafar
 * 
 */
public class ResourceNotFoundException extends JclException {
    /**
     * Default serial id
     */
    private static final long serialVersionUID = 1L;

    private String resourceName;
    private ResourceType resourceType;

    /**
     * Default constructor
     */
    public ResourceNotFoundException() {
        super();
    }

    /**
     * @param message
     */
    public ResourceNotFoundException(String message) {
        super( message );
    }

    /**
     * @param resource
     * @param message
     */
    public ResourceNotFoundException(String resource, String message) {
        super( message );
        resourceName = resource;
        determineResourceType( resource );
    }

    /**
     * @param e
     * @param resource
     * @param message
     */
    public ResourceNotFoundException(Throwable e, String resource, String message) {
        super( message, e );
        resourceName = resource;
        determineResourceType( resource );
    }

    /**
     * @param resourceName
     */
    private void determineResourceType(String resourceName) {
        if( resourceName.toLowerCase().endsWith( "." + ResourceType.CLASS.name().toLowerCase() ) )
            resourceType = ResourceType.CLASS;
        else if( resourceName.toLowerCase().endsWith( "." + ResourceType.PROPERTIES.name().toLowerCase() ) )
            resourceType = ResourceType.PROPERTIES;
        else if( resourceName.toLowerCase().endsWith( "." + ResourceType.XML.name().toLowerCase() ) )
            resourceType = ResourceType.XML;
        else
            resourceType = ResourceType.UNKNOWN;
    }

    /**
     * @return {@link ResourceType}
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * @param resourceName
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    /**
     * @return {@link ResourceType}
     */
    public ResourceType getResourceType() {
        return resourceType;
    }

    /**
     * @param resourceType
     */
    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }
}
