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

package org.jboss.webbeans.introspector.jlr;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.webbeans.BindingType;
import javax.webbeans.Produces;
import javax.webbeans.manager.Manager;

import org.jboss.webbeans.ManagerImpl;
import org.jboss.webbeans.injection.InjectionPointProvider;
import org.jboss.webbeans.introspector.AnnotatedMember;
import org.jboss.webbeans.introspector.AnnotatedParameter;
import org.jboss.webbeans.util.Reflections;
import org.jboss.webbeans.util.Strings;

import com.google.common.collect.ForwardingMap;

/**
 * Represents an abstract annotated memeber (field, method or constructor)
 * 
 * This class is immutable, and therefore threadsafe
 * 
 * @author Pete Muir
 * 
 * @param <T>
 * @param <S>
 */
public abstract class AbstractAnnotatedMember<T, S extends Member> extends AbstractAnnotatedItem<T, S> implements AnnotatedMember<T, S>
{
   /**
    * An annotation type -> list of annotations map
    */
   protected class AnnotatedParameterMap extends ForwardingMap<Class<? extends Annotation>, List<AnnotatedParameter<?>>>
   {
      private Map<Class<? extends Annotation>, List<AnnotatedParameter<?>>> delegate;

      public AnnotatedParameterMap()
      {
         delegate = new HashMap<Class<? extends Annotation>, List<AnnotatedParameter<?>>>();
      }

      @Override
      protected Map<Class<? extends Annotation>, List<AnnotatedParameter<?>>> delegate()
      {
         return delegate;
      }

      public void put(Class<? extends Annotation> key, AnnotatedParameter<?> value)
      {
         List<AnnotatedParameter<?>> parameters = super.get(key);
         if (parameters == null)
         {
            parameters = new ArrayList<AnnotatedParameter<?>>();
            super.put(key, parameters);
         }
         parameters.add(value);
      }

      @Override
      public String toString()
      {
         return Strings.mapToString("AnnotatedParameterMap (annotation type -> parameter abstraction list): ", delegate);
      }

      @Override
      public List<AnnotatedParameter<?>> get(Object key)
      {
         List<AnnotatedParameter<?>> parameters = super.get(key);
         return parameters != null ? parameters : new ArrayList<AnnotatedParameter<?>>();
      }
   }

   // The name of the member
   private final String name;

   // Cached string representation
   private String toString;
   
   private final boolean _public;

   /**
    * Constructor
    * 
    * @param annotationMap The annotation map
    */
   public AbstractAnnotatedMember(AnnotationMap annotationMap, AnnotationMap declaredAnnotationMap, Member member)
   {
      super(annotationMap, declaredAnnotationMap);
      name = member.getName();
      _public = Modifier.isPublic(member.getModifiers());
   }

   /**
    * Indicates if the member is static
    * 
    * @return True if static, false otherwise
    * 
    * @see org.jboss.webbeans.introspector.AnnotatedItem#isStatic()
    */
   public boolean isStatic()
   {
      return Reflections.isStatic(getDelegate());
   }

   /**
    * Indicates if the member if final
    * 
    * @return True if final, false otherwise
    * 
    * @see org.jboss.webbeans.introspector.AnnotatedItem#isFinal()
    */
   public boolean isFinal()
   {
      return Reflections.isFinal(getDelegate());
   }

   public boolean isTransient()
   {
      return Reflections.isTransient(getDelegate());
   }
   
   public boolean isPublic()
   {
      return _public;
   }

   /**
    * Gets the current value of the member
    * 
    * @param manager The Web Beans manager
    * @return The current value
    */
   public T getValue(Manager manager)
   {
      return manager.getInstanceByType(getType(), getMetaAnnotationsAsArray(BindingType.class));
   }

   /**
    * Gets the name of the member
    * 
    * @returns The name
    * 
    * @see org.jboss.webbeans.introspector.AnnotatedItem#getName()
    */
   public String getName()
   {
      return name;
   }

   /**
    * Gets a string representation of the member
    * 
    * @return A string representation
    */
   @Override
   public String toString()
   {
      if (toString != null)
      {
         return toString;
      }
      toString = "Abstract annotated member " + getName();
      return toString;
   }

   public S getMember()
   {
      return getDelegate();
   }

   /**
    * Helper method for getting the current parameter values from a list of
    * annotated parameters.
    * 
    * @param parameters The list of annotated parameter to look up
    * @param manager The Web Beans manager
    * @return The object array of looked up values
    */
   protected Object[] getParameterValues(List<AnnotatedParameter<?>> parameters, ManagerImpl manager)
   {
      return getParameterValues(parameters, null, null, manager);
   }

   /**
    * Helper method for getting the current parameter values from a list of
    * annotated parameters.
    * 
    * @param parameters The list of annotated parameter to look up
    * @param manager The Web Beans manager
    * @return The object array of looked up values
    */
   protected Object[] getParameterValues(List<AnnotatedParameter<?>> parameters, Object specialVal, Class<? extends Annotation> specialParam, ManagerImpl manager)
   {
      Object[] parameterValues = new Object[parameters.size()];
      boolean producerMethod = this.isAnnotationPresent(Produces.class);
      InjectionPointProvider injectionPointProvider = manager.getInjectionPointProvider();
      Iterator<AnnotatedParameter<?>> iterator = parameters.iterator();
      for (int i = 0; i < parameterValues.length; i++)
      {
         AnnotatedParameter<?> param = iterator.next();
         if (specialParam != null && param.isAnnotationPresent(specialParam))
         {
            parameterValues[i] = specialVal;
         }
         else
         {
            if (!producerMethod)
            {
               injectionPointProvider.pushInjectionPoint(param);
            }
            try
            {
               parameterValues[i] = param.getValue(manager);
            }
            finally
            {
               if (!producerMethod)
               {
                  injectionPointProvider.popInjectionPoint();
               }
            }
         }
      }
      return parameterValues;
   }

}
