/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ParamNameResolver {

  private static final String GENERIC_NAME_PREFIX = "param";

  /**
   * 参数索引和参数名称的映射
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    final Class<?>[] paramTypes = method.getParameterTypes();
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;

    // 循环参数，解析参数名称：@Param -> Method.parameters -> 索引位0/1/2...
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {

      // 是否特殊参数类型：RowBounds、ResultHandler
      // 特殊参数类型无需解析，后续逻辑中会有处理
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }

      // 初始化名称
      String name = null;

      // 循环方法参数的注解，获取@Param注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param)annotation).value();
          break;
        }
      }

      // 没有@Param注解，则从方法上获取参数名称，如果编译时有开启parameters，则可以获取到实际的参数名称，否则只能获取到arg0、arg1...这一类的参数名称
      if (name == null) {
        // @Param was not specified.
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }

        // 如果参数名称还是空，则采用map.size作为参数名称，其实就是把索引作为参数名称
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }

      // 将索引和参数名称放入集合中
      map.put(paramIndex, name);
    }

    // 将map转为有序不可变map，赋值给names
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();

    // 如果没有参数，则直接返回null
    if (args == null || paramCount == 0) {
      return null;
    }

    // 有一个参数，且没有@Param注解，则直接返回参数数据本身，需要注意的是，这里的参数数据可能最终会被DefaultSqlSession.wrapCollection处理
    // 如果方法没有指定@Param注解，且方法只有一个参数，且方法参数是Collection或Array，结合DefaultSqlSession
    // .wrapCollection的逻辑，最终会导致生成一个{"collection":obj,"list":obj,"array":obj}的参数映射集合，如果有某些xml节点（forEach、if..
    // .）需要解析，可能会因为参数名称映射问题而出现异常
    // 也就是：如果参数只有一个，且参数本身是集合或数组，会导致参数名称被重命名为collection/list/array
    // 这个算是个问题，在3.5.5版本被修复了：https://github.com/mybatis/mybatis-3/issues/1237
    else if (!hasParamAnnotation && paramCount == 1) {
      return args[names.firstKey()];
    }

    // 否则生成参数名称和参数数据的映射集合
    else {
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {

        // 生成参数名称和参数数据的映射
        param.put(entry.getValue(), args[entry.getKey()]);

        // 添加通用参数名称和参数数据的映射
        // add generic param names (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);

        // 确保不会把原先就有的参数名称覆盖掉：如果用户本身已经执行param1这种类型的注解的话，就不生成通用参数名称和参数数据的映射
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
