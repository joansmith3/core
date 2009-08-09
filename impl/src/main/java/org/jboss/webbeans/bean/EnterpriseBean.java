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
package org.jboss.webbeans.bean;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;

import javax.decorator.Decorator;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.CreationException;
import javax.interceptor.Interceptor;

import org.jboss.webbeans.BeanManagerImpl;
import org.jboss.webbeans.DefinitionException;
import org.jboss.webbeans.bean.proxy.EnterpriseBeanInstance;
import org.jboss.webbeans.bean.proxy.EnterpriseBeanProxyMethodHandler;
import org.jboss.webbeans.bean.proxy.Marker;
import org.jboss.webbeans.bootstrap.BeanDeployerEnvironment;
import org.jboss.webbeans.ejb.InternalEjbDescriptor;
import org.jboss.webbeans.ejb.api.SessionObjectReference;
import org.jboss.webbeans.ejb.spi.BusinessInterfaceDescriptor;
import org.jboss.webbeans.ejb.spi.EjbServices;
import org.jboss.webbeans.introspector.WBClass;
import org.jboss.webbeans.introspector.WBMethod;
import org.jboss.webbeans.log.Log;
import org.jboss.webbeans.log.Logging;
import org.jboss.webbeans.util.Proxies;

/**
 * An enterprise bean representation
 * 
 * @author Pete Muir
 * 
 * @param <T> The type (class) of the bean
 */
public class EnterpriseBean<T> extends AbstractClassBean<T>
{
   private final Log log = Logging.getLog(EnterpriseBean.class);

   // The EJB descriptor
   private InternalEjbDescriptor<T> ejbDescriptor;

   private Class<T> proxyClass;

   private EnterpriseBean<?> specializedBean;

   /**
    * Creates a simple, annotation defined Enterprise Web Bean
    * 
    * @param <T> The type
    * @param clazz The class
    * @param manager the current manager
    * @return An Enterprise Web Bean
    */
   public static <T> EnterpriseBean<T> of(WBClass<T> clazz, BeanManagerImpl manager, BeanDeployerEnvironment environment)
   {
      return new EnterpriseBean<T>(clazz, manager, environment);
   }

   /**
    * Constructor
    * 
    * @param type The type of the bean
    * @param manager The Web Beans manager
    */
   protected EnterpriseBean(WBClass<T> type, BeanManagerImpl manager, BeanDeployerEnvironment environment)
   {
      super(type, manager);
      initType();
      Iterable<InternalEjbDescriptor<T>> ejbDescriptors = environment.getEjbDescriptors().get(getType());
      if (ejbDescriptors == null)
      {
         throw new DefinitionException("Not an EJB " + toString());
      }
      for (InternalEjbDescriptor<T> ejbDescriptor : ejbDescriptors)
      {
         if (this.ejbDescriptor == null)
         {
            this.ejbDescriptor = ejbDescriptor;
         }
         else
         {
            throw new RuntimeException("TODO Multiple EJBs have the same bean class! " + getType());
         }
      }
      initTypes();
      initBindings();
   }

   /**
    * Initializes the bean and its metadata
    */
   @Override
   public void initialize(BeanDeployerEnvironment environment)
   {
      if (!isInitialized())
      {
         super.initialize(environment);
         initProxyClass();
         checkEJBTypeAllowed();
         checkConflictingRoles();
         checkObserverMethods();
         checkScopeAllowed();
      }
   }

   @Override
   protected void initTypes()
   {
      Set<Type> types = new HashSet<Type>();
      types = new LinkedHashSet<Type>();
      types.add(Object.class);
      for (BusinessInterfaceDescriptor<?> businessInterfaceDescriptor : ejbDescriptor.getLocalBusinessInterfaces())
      {
         types.add(businessInterfaceDescriptor.getInterface());
      }
      super.types = types;
   }

   protected void initProxyClass()
   {
      Set<Type> types = new LinkedHashSet<Type>(getTypes());
      types.add(EnterpriseBeanInstance.class);
      types.add(Serializable.class);
      ProxyFactory proxyFactory = Proxies.getProxyFactory(types);

      @SuppressWarnings("unchecked")
      Class<T> proxyClass = proxyFactory.createClass();

      this.proxyClass = proxyClass;
   }

   /**
    * Validates for non-conflicting roles
    */
   protected void checkConflictingRoles()
   {
      if (getType().isAnnotationPresent(Interceptor.class))
      {
         throw new DefinitionException("Enterprise beans cannot be interceptors");
      }
      if (getType().isAnnotationPresent(Decorator.class))
      {
         throw new DefinitionException("Enterprise beans cannot be decorators");
      }
   }

   /**
    * Check that the scope type is allowed by the stereotypes on the bean and
    * the bean type
    */
   protected void checkScopeAllowed()
   {
      if (ejbDescriptor.isStateless() && !isDependent())
      {
         throw new DefinitionException("Scope " + getScopeType() + " is not allowed on stateless enterpise beans for " + getType() + ". Only @Dependent is allowed on stateless enterprise beans");
      }
      if (ejbDescriptor.isSingleton() && !(isDependent() || getScopeType().equals(ApplicationScoped.class)))
      {
         throw new DefinitionException("Scope " + getScopeType() + " is not allowed on singleton enterpise beans for " + getType() + ". Only @Dependent or @ApplicationScoped is allowed on singleton enterprise beans");
      }
   }

   /**
    * Validates specialization
    */
   @Override
   protected void preSpecialize(BeanDeployerEnvironment environment)
   {
      super.preSpecialize(environment);
      if (!environment.getEjbDescriptors().containsKey(getAnnotatedItem().getWBSuperclass().getJavaClass()))
      {
         throw new DefinitionException("Annotation defined specializing EJB must have EJB superclass");
      }
   }

   @Override
   protected void specialize(BeanDeployerEnvironment environment)
   {
      if (environment.getClassBean(getAnnotatedItem().getWBSuperclass()) == null)
      {
         throw new IllegalStateException(toString() + " does not specialize a bean");
      }
      AbstractClassBean<?> specializedBean = environment.getClassBean(getAnnotatedItem().getWBSuperclass());
      if (!(specializedBean instanceof EnterpriseBean))
      {
         throw new IllegalStateException(toString() + " doesn't have a session bean as a superclass " + specializedBean);
      }
      else
      {
         this.specializedBean = (EnterpriseBean<?>) specializedBean; 
      }
   }

   /**
    * Creates an instance of the bean
    * 
    * @return The instance
    */
   public T create(final CreationalContext<T> creationalContext)
   {
      T instance = produce(creationalContext);
      if (hasDecorators())
      {
         instance = applyDecorators(instance, creationalContext, null);
      }
      return instance;
   }
   
   public void inject(T instance, CreationalContext<T> ctx)
   {
      throw new UnsupportedOperationException("Unable to perform injection on a session bean");
   }

   public void postConstruct(T instance)
   {
      throw new UnsupportedOperationException("Unable to call postConstruct() on a session bean");
   }

   public void preDestroy(T instance)
   {
      throw new UnsupportedOperationException("Unable to call preDestroy() on a session bean");
   }

   public T produce(CreationalContext<T> ctx)
   {
      try
      {
         T instance = proxyClass.newInstance();
         ctx.push(instance);
         ((ProxyObject) instance).setHandler(new EnterpriseBeanProxyMethodHandler<T>(this, ctx));
         log.trace("Enterprise bean instance created for bean {0}", this);
         return instance;
      }
      catch (InstantiationException e)
      {
         throw new RuntimeException("Could not instantiate enterprise proxy for " + toString(), e);
      }
      catch (IllegalAccessException e)
      {
         throw new RuntimeException("Could not access bean correctly when creating enterprise proxy for " + toString(), e);
      }
      catch (Exception e)
      {
         throw new CreationException("could not find the EJB in JNDI " + proxyClass, e);
      }
   }

   public void destroy(T instance, CreationalContext<T> creationalContext)
   {
      if (instance == null)
      {
         throw new IllegalArgumentException("instance to destroy cannot be null");
      }
      if (!(instance instanceof EnterpriseBeanInstance))
      {
         throw new IllegalArgumentException("Cannot destroy session bean instance not created by the container");
      }
      EnterpriseBeanInstance enterpiseBeanInstance = (EnterpriseBeanInstance) instance;
      
      if (enterpiseBeanInstance.isDestroyed(Marker.INSTANCE))
      {
         return;
      }
      else
      {
         enterpiseBeanInstance.destroy(Marker.INSTANCE, this, creationalContext);
      }
      creationalContext.release();
   }

   /**
    * Validates the bean type
    */
   private void checkEJBTypeAllowed()
   {
      if (ejbDescriptor.isMessageDriven())
      {
         throw new DefinitionException("Message Driven Beans can't be Web Beans");
      }
   }

   /**
    * Gets a string representation
    * 
    * @return The string representation
    */
   @Override
   public String toString()
   {
      StringBuilder buffer = new StringBuilder();
      // buffer.append("Annotated " + Names.scopeTypeToString(getScopeType()) +
      // Names.ejbTypeFromMetaData(getEjbMetaData()));
      if (getName() == null)
      {
         buffer.append(" unnamed enterprise bean");
      }
      else
      {
         buffer.append(" enterprise bean '" + getName() + "'");
      }
      buffer.append(" [" + getType().getName() + "] ");
      buffer.append("API types " + getTypes() + ", binding types " + getBindings());
      return buffer.toString();
   }

   public void postConstruct(T instance, CreationalContext<T> creationalContext)
   {
      injectBoundFields(instance, creationalContext);
      callInitializers(instance, creationalContext);
   }

   public void preDestroy(CreationalContext<T> creationalContext)
   {
      creationalContext.release();
   }
   
   @Override
   protected void initSerializable()
   {
      // No-op
   }
   
   @Override
   public boolean isSerializable()
   {
      return true;
   }

   public InternalEjbDescriptor<T> getEjbDescriptor()
   {
      return ejbDescriptor;
   }

   public boolean isClientCanCallRemoveMethods()
   {
      return getEjbDescriptor().isStateful() && isDependent();
   }

   @Override
   public AbstractBean<?, ?> getSpecializedBean()
   {
      return specializedBean;
   }

   /**
    * If there are any observer methods, they must be static or business
    * methods.
    */
   protected void checkObserverMethods()
   {
      for (WBMethod<?, ?> method : this.annotatedItem.getWBDeclaredMethodsWithAnnotatedParameters(Observes.class))
      {
         if (!method.isStatic())
         {
            if (!isMethodExistsOnTypes(method))
            {
               throw new DefinitionException("Observer method must be static or business method: " + method + " on " + getAnnotatedItem());
            }
         }
      }
   }
   
   // TODO must be a nicer way to do this!
   public boolean isMethodExistsOnTypes(WBMethod<?, ?> method)
   {
      for (Type type : getTypes())
      {
         if (type instanceof Class)
         {
            for (Method m : ((Class<?>) type).getMethods())
            {
               if (method.getName().equals(m.getName()) && Arrays.equals(method.getParameterTypesAsArray(), m.getParameterTypes()))
               {
                  return true;
               }
            }
         }
      }
      return false;
   }
   
   public SessionObjectReference createReference()
   {
      return manager.getServices().get(EjbServices.class).resolveEjb(getEjbDescriptor().delegate());
   }

   public Set<Class<? extends Annotation>> getStereotypes()
   {
      return Collections.emptySet();
   }
   
}

