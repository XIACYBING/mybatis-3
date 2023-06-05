/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 参数名称工具类，主要是使用{@link Method}和{@link Constructor}的父类{@link Executable#getParameters()}获取参数数组，进而获取到参数名称
 * <p>
 * 如果编译时指定的{@code parameters}参数，则{@code javac}编译源码为字节码时会保留方法参数名称，否则会将方法参数名称编译成默认的{@code arg0、arg1、arg2...}，因此MyBatis
 * 的SQL中可以通过这些参数名称获取参数值
 * <p>
 * 其实字节码中还有一个{@code LocalVariableTable}，会存储方法中的属性信息，包含属性名称，但是Mapper的类型一般是接口，接口上的抽象方法在编译时一般不会生成方法体，自然也不会有{@code
 * LocalVariableTable}
 * <p>
 * 这里其实还有一个针对非抽象/本地方法的问题，如果非抽象/本地方法编译时会生成{@code LocalVariableTable}，那么{@link Executable#getParameters()}为什么不从{@code
 * LocalVariableTable}中获取参数名称呢？猜测是因为{@code LocalVariableTable}的定义：It may be used by debuggers to determine the value
 * of a given local variable during the execution of a method. 也就是说，{@code LocalVariableTable}更多是用来在方法执行期间debug参数信息的
 * <p>
 * 而Spring框架的控制器参数解析流程中，有通过读取类字节码获取{@code LocalVariableTable}信息，从而获取到方法参数的实际名称：org.springframework.core.LocalVariableTableParameterNameDiscoverer
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.13">4.7.13. The LocalVariableTable Attribute</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.3">4.7.3. The Code Attribute</a>
 * @see <a href="https://stackoverflow.com/questions/71515303/why-is-there-no-localvariabletable-in-the-class-file-compiled-with-javac-g-for">Why is there no LocalVariableTable in the class file compiled with javac -g for the Java interface?</a>
 * @see <a href="https://www.google.com/search?q=Why+does+the+method+not+obtain+parameter+names+from+LocalVariableTable+in+bytecode+file">Why does the method not obtain parameter names from LocalVariableTable in bytecode file</a>
 */
public class ParamNameUtil {
  public static List<String> getParamNames(Method method) {
    return getParameterNames(method);
  }

  public static List<String> getParamNames(Constructor<?> constructor) {
    return getParameterNames(constructor);
  }

  private static List<String> getParameterNames(Executable executable) {
    return Arrays.stream(executable.getParameters()).map(Parameter::getName).collect(Collectors.toList());
  }

  private ParamNameUtil() {
    super();
  }
}
