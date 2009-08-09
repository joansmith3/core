/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.webbeans;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;

import org.jboss.webbeans.injection.ConstructorInjectionPoint;
import org.jboss.webbeans.injection.FieldInjectionPoint;
import org.jboss.webbeans.injection.MethodInjectionPoint;
import org.jboss.webbeans.introspector.WBClass;
import org.jboss.webbeans.introspector.WBMethod;
import org.jboss.webbeans.util.Beans;

/**
 * @author pmuir
 *
 */
public class SimpleInjectionTarget<T> implements InjectionTarget<T>
{
 
   private final BeanManagerImpl beanManager;
   private final WBClass<T> type;
   private final ConstructorInjectionPoint<T> constructor;
   private final Set<FieldInjectionPoint<?, ?>> injectableFields;
   private final Set<MethodInjectionPoint<?, ?>> initializerMethods;
   private final WBMethod<?, ?> postConstruct;
   private final WBMethod<?, ?> preDestroy;
   private final Set<InjectionPoint> injectionPoints;

   public SimpleInjectionTarget(WBClass<T> type, BeanManagerImpl beanManager)
   {
      this.beanManager = beanManager;
      this.type = type;
      this.injectionPoints = new HashSet<InjectionPoint>();
      this.constructor = Beans.getBeanConstructor(null, type);
      this.injectionPoints.addAll(Beans.getParameterInjectionPoints(null, constructor));
      this.injectableFields = new HashSet<FieldInjectionPoint<?,?>>();
      this.injectableFields.addAll(Beans.getFieldInjectionPoints(null, type));
      this.injectionPoints.addAll(injectableFields);
      this.initializerMethods = new HashSet<MethodInjectionPoint<?,?>>();
      this.initializerMethods.addAll(Beans.getInitializerMethods(null, type));
      this.injectionPoints.addAll(Beans.getParameterInjectionPoints(null, initializerMethods));
      this.postConstruct = Beans.getPostConstruct(type);
      this.preDestroy = Beans.getPreDestroy(type);
   }

   public T produce(CreationalContext<T> ctx)
   {
      return constructor.newInstance(beanManager, ctx);
   }
   
   public void inject(T instance, CreationalContext<T> ctx)
   {
      for (FieldInjectionPoint<?, ?> injectionPoint : injectableFields)
      {
         injectionPoint.inject(instance, beanManager, ctx);
      }
   }

   public void postConstruct(T instance)
   {
      try
      {
         postConstruct.invoke(instance);
      }
      catch (Exception e)
      {
         throw new RuntimeException("Error invoking postConstruct() " + postConstruct, e);
      }
   }

   public void preDestroy(T instance)
   {
      try
      {
         preDestroy.invoke(instance);
      }
      catch (Exception e)
      {
         throw new RuntimeException("Error invoking preDestroy() " + preDestroy, e);
      }
   }

   public void dispose(T instance)
   {
      // No-op
   }

   public Set<InjectionPoint> getInjectionPoints()
   {
      return injectionPoints;
   }

}
