package org.touchhome.app.js.assistant.impl;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.touchhome.app.js.assistant.model.Completion;
import org.touchhome.app.js.assistant.model.CompletionItemKind;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.utils.JavaScriptBinder;
import org.touchhome.common.exception.ServerException;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CodeParser {

    private final ClassFinder classFinder;

    private static List<String> splitSpecial(String orig) {
        List<String> parts = new ArrayList<>();
        int nextingLevel = 0;
        StringBuilder result = new StringBuilder();
        for (char c : orig.toCharArray()) {
            if (c == '.' && nextingLevel == 0) {
                parts.add(result.toString().trim());
                result.setLength(0);// clean buffer
            } else {
                if (c == '(') {
                    nextingLevel++;
                }
                if (c == ')') {
                    nextingLevel--;
                }
                result.append(c);
            }
        }
        // Thanks PoeHah for pointing it out. This adds the last element to it.
        parts.add(result.toString().trim());
        return parts;
    }

    public static String removeAllComments(String line) {
        while (true) {
            if (line.contains("/*") && line.contains("*/")) {
                line = line.substring(0, line.indexOf("/*")) + line.substring(line.indexOf("*/") + 2);
            } else {
                return line;
            }
        }
    }

    /**
     * if firstItem is var type, evaluated before
     */
    private Set<Completion> getCompletionsForVarType(String varName, String allScript, String line) throws NoSuchMethodException {
        Pattern searchVarPattern = Pattern.compile("var[ ]+" + varName + "[ ]*=");
        Matcher matcher = searchVarPattern.matcher(allScript);
        if (matcher.find()) {
            int endIndex = matcher.end();
            String varValue = allScript.substring(endIndex, allScript.indexOf(";", endIndex));
            ParserContext.TypeParserContext context = ParserContext.varTypeContext();
            addCompetitionFromManagerOrClass(varValue, new Stack<>(), context, allScript);
            if (context.varType.getUpdateCount() > 0) {
                return addCompetitionFrom(varName, context.varType.getValue(), line, new Stack<>(), context);
            }
        }
        return Collections.emptySet();
    }

    private Set<Completion> addCompetitionFrom(String from, Class fromClass, String line, Stack<Param> stack, ParserContext context) throws NoSuchMethodException {
        line = line.trim();
        Set<Completion> list = new HashSet<>();
        List<String> items = splitSpecial(line);

        String firstItem = items.get(0);

        if (line.equals("new")) {
            createClassAndAddToCompletion(fromClass, list);
        } else if (firstItem.endsWith(from) && StringUtils.isNotEmpty(from)) {
            stack.add(new Param(from, fromClass));
            addCompetitionFromManager2(1, items, list, fromClass, stack, context);
        } else if (from.startsWith(firstItem) && StringUtils.isNotEmpty(from)) {
            list.add(new Completion(from, fromClass.getSimpleName(), fromClass, "",
                    CompletionItemKind.Method));
        } else { // check for Class
            if (fromClass.getSimpleName().equals(from)) {
                stack.add(new Param(from, fromClass));
                addCompetitionFromStatic(StringUtils.isEmpty(from) ? 0 : 1, items, list, fromClass, stack);
            } else {
                List<Class<?>> classesWithParent = classFinder.getClassesWithParent(Object.class, firstItem, null);
                if (classesWithParent.size() == 1) {
                    fromClass = classesWithParent.get(0);
                    stack.add(new Param(from, fromClass));
                    addCompetitionFromStatic(StringUtils.isEmpty(from) ? 0 : 1, items, list, fromClass, stack);
                }
            }
        }

        return list;
    }

    public Set<Completion> addCompetitionFromManagerOrClass(String line, Stack<Param> stack, ParserContext context, String allScript) throws NoSuchMethodException {
        Set<Completion> completions = new HashSet<>();
        List<String> items = splitSpecial(line); // list of items splitted by '.'
        String firstItem = items.get(0);
        // check if firstItem is previous evaluated var
        FirsItemType firsItemType = FirsItemType.find(firstItem, line, allScript);
        if (firsItemType != null) {
            return getCompletionsForVarType(firstItem, allScript, line);
        }
        // try evaluate with
        for (JavaScriptBinder javaScriptBinder : JavaScriptBinder.values()) {
            Class clazz = javaScriptBinder.managerClass;
            completions.addAll(addCompetitionFrom(javaScriptBinder.name(), clazz, line, stack, context));
        }
        if (completions.isEmpty()) {
            return tryEvaFromFunctionParameter(firstItem, allScript, line);
        }
        return completions;
    }

    private Set<Completion> tryEvaFromFunctionParameter(String supposeParameter, String allScript, String line) throws NoSuchMethodException {
        int index = allScript.indexOf(line);
        int funcIndex = allScript.lastIndexOf("function", index);
        int endFunc = allScript.indexOf(")", funcIndex);
        if (endFunc != -1) {
            String funcParameters = allScript.substring(allScript.indexOf("(", funcIndex) + 1, endFunc);
            if (funcParameters.contains(supposeParameter)) {
                String lineToParse = allScript.substring(allScript.lastIndexOf("context.", funcIndex), funcIndex);
                Stack<CodeParser.Param> stack2 = new Stack<>();
                ParserContext context2 = ParserContext.noneContext();
                addCompetitionFromManagerOrClass(lineToParse.trim(), stack2, context2, allScript);
                return addCompetitionFrom(supposeParameter, stack2.peek().retType, line, new Stack<>(), context2);
            }
        }
        return Collections.emptySet();
    }

    private void createClassAndAddToCompletion(Class fromClass, Set<Completion> list) {
        StringBuilder content = new StringBuilder(fromClass.getName() + "()");
        if (fromClass.isInterface()) {
            content.append("{\n\t");
            for (Method method : fromClass.getMethods()) {
                if (!method.isDefault()) {
                    content.append(method.getReturnType().getName()).append(" ").append(method.getName()).append(createParametersFromMethod(method, false)).append(" {\n\t\t\n\t}");
                }
            }
            content.append("}");
        }
        content.append(";");
        list.add(new Completion(content.toString(), fromClass.getSimpleName(), fromClass, "",
                CompletionItemKind.Method));
    }

    private void addStaticCompletions(String finalNext, Class clazz, Set<Completion> list) {
        if (clazz != null) {
            list.addAll(getFitStaticMethods(finalNext, clazz).stream().map(this::convertMethodToCompletion).collect(Collectors.toList()));
            list.addAll(getFitStaticFields(finalNext, clazz).stream().map(this::convertFieldToCompletion).collect(Collectors.toList()));
        }
    }

    private void addCompletionsAtEndFunc(String finalNext, Class clazz, Set<Completion> list, Stack<Param> stack, ParserContext context) throws NoSuchMethodException {
        if (clazz != null) {
            if (!finalNext.contains("(")) {
                list.addAll(MethodParser.getFitMethods(finalNext, clazz).stream().map(this::convertMethodToCompletion).collect(Collectors.toList()));
            } else {
                String funcParameters = finalNext.substring(finalNext.indexOf("(") + 1);
                int paramIndex = StringUtils.countMatches(funcParameters, ",");
                Class fromClass = Object.class;

                // find function parameter class from index
                Set<Method> fitMethods = MethodParser.getFitMethods(finalNext, clazz);
                if (!fitMethods.isEmpty()) {
                    Method method = fitMethods.iterator().next();

                    // maybe need evaluate type
                    if (context.is(ParserContext.ContextType.VarEvaluate)) {
                        ParserContext.TypeParserContext typeParserContext = (ParserContext.TypeParserContext) context;
                        fitMethods.forEach(method1 -> typeParserContext.typeConsumer.accept(method1.getReturnType()));
                        return;
                    }
                    Type[] genericParameterTypes = method.getGenericParameterTypes();
                    if (genericParameterTypes.length > paramIndex) {
                        Type type = genericParameterTypes[paramIndex];
                        if (type instanceof Class) {
                            fromClass = (Class) type;
                        }
                    }
                }

                if (funcParameters.contains(" ")) {
                    funcParameters = funcParameters.substring(funcParameters.lastIndexOf(" "));
                }
                // try look at parameters
                list.addAll(addCompetitionFrom("context", fromClass, funcParameters, stack, context));
            }
        }
    }

    private Completion convertMethodToCompletion(Method method) {
        try {
            String parameters = createParametersFromMethod(method, true);

            String retValue;
            if (method.getAnnotatedReturnType() instanceof AnnotatedParameterizedType) {
                List<String> retParameters = Stream.of(((AnnotatedParameterizedType) method.getAnnotatedReturnType()).getAnnotatedActualTypeArguments())
                        .filter(a -> a.getType() instanceof Class)
                        .map(a -> ((Class) a.getType()).getSimpleName()).collect(Collectors.toList());
                retValue = method.getReturnType().getSimpleName() + "<" + StringUtils.join(retParameters, ", ") + ">";
            } else {
                retValue = method.getReturnType().getSimpleName();
            }
            ApiOperation apiOperation = method.getDeclaredAnnotation(ApiOperation.class);
            String help = apiOperation == null ? "" : apiOperation.value();

            return new Completion(method.getName() + parameters, retValue, method.getReturnType(), help,
                    CompletionItemKind.Method);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ServerException(ex);
        }
    }

    private String createParametersFromMethod(Method method, boolean asComments) {
        int apiParamIndex = 0;
        Annotation[] apiParams = method.getParameterAnnotations().length == 0 ? new Annotation[0] : method.getParameterAnnotations()[0];
        List<String> collect = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            String name = parameter.getName();
            if (apiParams.length > apiParamIndex) {
                name = ((ApiParam) apiParams[apiParamIndex++]).value();
            }
            collect.add(parameter.getType().getSimpleName() + (asComments ? ":" : " ") + name);
        }

        return collect.isEmpty() ? "()" : "(" + (asComments ? "/*" : "") +
                StringUtils.join(collect, (asComments ? "*/" : "") + "   , " + (asComments ? "/*" : "") + " ") + (asComments ? "*/" : "") + ")";
    }

    private Completion convertFieldToCompletion(Field field) {
        try {
            return new Completion(field.getName(), field.getType().getSimpleName(), field.getType(), "",
                    CompletionItemKind.Method);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ServerException(ex);
        }
    }

    private Set<Field> getFitStaticFields(String finalNext, Class clazz) {
        Set<Field> fields = new HashSet<>();
        for (Field field : clazz.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                if (field.getName().startsWith(finalNext)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private Set<Method> getFitStaticMethods(String finalNext, Class clazz) {
        Set<Method> methods = new HashSet<>();
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                String methodPrefix = finalNext.indexOf("(") > 0 ? finalNext.substring(0, finalNext.indexOf("(")) : finalNext;
                if (method.getName().startsWith(methodPrefix)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private Method getFitStaticMethod(String finalNext, Class clazz) {
        return getFitMethod(Stream.of(clazz.getMethods()), finalNext, method -> Modifier.isStatic(method.getModifiers()));
    }

    private Method getFitMethod(Stream<Method> stream, String finalNext, Predicate<Method> testPredicate) {
        final Method[] fitMethod = {null};
        final float[] chance = {0};

        stream.filter(testPredicate).forEach(method -> {
            String methodPrefix = finalNext.indexOf("(") > 0 ? finalNext.substring(0, finalNext.indexOf("(")) : finalNext;
            if (method.getName().equals(methodPrefix)) {
                chance[0] = 1f;
                fitMethod[0] = method;
            } else if (chance[0] < 1) {
                chance[0] = 0.5f;
                fitMethod[0] = method;
            }
        });
        return fitMethod[0];
    }

    private Method getFitMethod(String finalNext, Class clazz) {
        Stream<Method> stream = clazz.getSuperclass() != null ? Stream.concat(Stream.of(clazz.getMethods()), Stream.of(clazz.getSuperclass().getMethods())) :
                Stream.of(clazz.getMethods());

        return getFitMethod(stream, finalNext, method -> true);
    }

    private void addCompetitionFromManager2(int i, List<String> items, Set<Completion> list, Class clazz, Stack<Param> stack, ParserContext context) throws NoSuchMethodException {
        if (items.size() == i + 1) {
            addCompletionsAtEndFunc(items.get(i), clazz, list, stack, context);
            addCompletionsAtEndFunc(items.get(i), clazz.getSuperclass(), list, stack, context);
        } else {
            Method methodOptional = getFitMethod(items.get(i), clazz);
            if (methodOptional != null) {
                stack.add(new Param(methodOptional, clazz));
                if (items.get(i).matches(".*\\(.*\\)")) {
                    addCompetitionFromManager2(i + 1, items, list, methodOptional.getReturnType(), stack, context);
                } else if (items.get(i).matches(".*\\(.*")) { // we have opened brackets so try look at method parameters
                    String[] methodParameters = items.get(i).substring(items.get(i).indexOf("(") + 1).split(",");
                    if (methodOptional.getParameterCount() >= methodParameters.length) {
                        Parameter parameter = methodOptional.getParameters()[methodParameters.length - 1];
                        // getPinRequestType
                        addCompetitionFrom("", parameter.getType(), methodParameters[methodParameters.length - 1], new Stack<>(), context);


                    }
                }
            }
        }
    }

    private void addCompetitionFromStatic(int i, List<String> items, Set<Completion> list, Class clazz, Stack<Param> stack) {
        if (items.size() == i + 1) {
            addStaticCompletions(items.get(i), clazz, list);
            addStaticCompletions(items.get(i), clazz.getSuperclass(), list);
        } else {
            Method methodOptional = getFitStaticMethod(items.get(i), clazz);
            if (methodOptional != null) {
                stack.add(new Param(methodOptional, clazz));
                addCompetitionFromStatic(i + 1, items, list, methodOptional.getReturnType(), stack);
            }
        }
    }

    private enum FirsItemType {
        Var {
            @Override
            protected boolean match(String firstItem, String line, String allScript) {
                try {
                    Pattern searchVarPattern = Pattern.compile("var[ ]+" + firstItem + "[ ]*=");
                    return searchVarPattern.matcher(allScript).find();
                } catch (Exception ex) {
                    return false;
                }
            }
        };

        public static FirsItemType find(String firstItem, String line, String allScript) {
            for (FirsItemType firstItemType : FirsItemType.values()) {
                if (firstItemType.match(firstItem, line, allScript)) {
                    return firstItemType;
                }
            }
            return null;
        }

        protected abstract boolean match(String firstItem, String line, String allScript);
    }

    public static class Param {
        public String method;
        Class retType;
        boolean isList;

        Param(String method, Class retType) {
            this.method = method;
            this.retType = retType;
        }

        Param(Method method, Class clazz) {
            this.isList = Collection.class.isAssignableFrom(method.getReturnType());
            this.method = method.getName();
            if (isList) {
                Type type = ((ParameterizedType) method.getAnnotatedReturnType().getType()).getActualTypeArguments()[0];
                if (type.getTypeName().equals("T")) {
                    retType = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
                } else {
                    retType = method.getReturnType();
                }
            }

        }
    }
}
