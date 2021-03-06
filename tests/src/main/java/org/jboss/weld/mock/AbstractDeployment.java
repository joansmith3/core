/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.mock;

import java.util.Collection;

import javax.enterprise.inject.spi.Extension;

import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.CDI11Deployment;
import org.jboss.weld.bootstrap.spi.Metadata;

public abstract class AbstractDeployment extends org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.AbstractDeployment implements CDI11Deployment {

    public AbstractDeployment(BeanDeploymentArchive beanDeploymentArchive) {
        super(beanDeploymentArchive, new Extension[0]);
    }

    public AbstractDeployment(BeanDeploymentArchive beanDeploymentArchive, Extension... extensions) {
        super(beanDeploymentArchive, extensions);
    }

    public AbstractDeployment(BeanDeploymentArchive... beanDeploymentArchives) {
        super(beanDeploymentArchives);
    }

    public AbstractDeployment(Collection<BeanDeploymentArchive> beanDeploymentArchives, Iterable<Metadata<Extension>> extensions) {
        super(beanDeploymentArchives, extensions);
    }

    @Override
    public BeanDeploymentArchive getBeanDeploymentArchive(Class<?> beanClass) {
        return loadBeanDeploymentArchive(beanClass);
    }
}
