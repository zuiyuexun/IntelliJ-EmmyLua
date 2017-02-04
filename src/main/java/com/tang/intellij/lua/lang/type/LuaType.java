/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
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

package com.tang.intellij.lua.lang.type;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.psi.stubs.StubOutputStream;
import com.tang.intellij.lua.editor.completion.FuncInsertHandler;
import com.tang.intellij.lua.lang.LuaIcons;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.index.LuaClassFieldIndex;
import com.tang.intellij.lua.stubs.index.LuaClassMethodIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

/**
 * 类型说明
 * Created by TangZX on 2016/12/4.
 */
public class LuaType {

    public static LuaType create(@NotNull String typeName, @Nullable String superTypeName) {
        LuaType type = new LuaType();
        type.clazzName = typeName;
        type.superClassName = superTypeName;
        return type;
    }

    public static LuaType createAnonymousType(LuaNameDef localDef) {
        return create(LuaPsiResolveUtil.getAnonymousType(localDef), null);
    }

    public static LuaType createGlobalType(LuaNameRef ref) {
        return create(ref.getText(), null);
    }

    protected LuaType() {
    }

    protected String clazzName;
    protected String superClassName;

    public LuaType getSuperClass(SearchContext context) {
        return null;
    }

    public String getClassNameText() {
        return clazzName;
    }

    public void serialize(@NotNull StubOutputStream stubOutputStream) throws IOException {
        stubOutputStream.writeName(clazzName);
        stubOutputStream.writeName(superClassName);
    }

    public void addMethodCompletions(@NotNull CompletionParameters completionParameters,
                                     @NotNull CompletionResultSet completionResultSet,
                                     boolean useAsField) {
        Project project = completionParameters.getEditor().getProject();
        assert project != null;
        addMethodCompletions(completionResultSet, project, true, useAsField);
    }

    private void addMethodCompletions(@NotNull CompletionResultSet completionResultSet,
                                      @NotNull Project project,
                                      boolean bold,
                                      boolean useAsField) {
        String clazzName = getClassNameText();
        if (clazzName == null)
            return;

        Collection<LuaClassMethodDef> list = LuaClassMethodIndex.getInstance().get(clazzName, project, new ProjectAndLibrariesScope(project));
        for (LuaClassMethodDef def : list) {
            String methodName = def.getName();
            if (methodName != null && completionResultSet.getPrefixMatcher().prefixMatches(methodName)) {
                LookupElementBuilder elementBuilder = LookupElementBuilder.create(methodName)
                        .withIcon(LuaIcons.CLASS_METHOD)
                        .withTypeText(clazzName);
                if (!useAsField)
                    elementBuilder = elementBuilder.withInsertHandler(new FuncInsertHandler(def.getFuncBody()));
                if (bold)
                    elementBuilder = elementBuilder.bold();

                completionResultSet.addElement(elementBuilder);
            }
        }

        LuaType superType = getSuperClass(new SearchContext(project));
        if (superType != null)
            superType.addMethodCompletions(completionResultSet, project, false, useAsField);
    }

    private void addStaticMethodCompletions(@NotNull CompletionResultSet completionResultSet,
                                            boolean bold,
                                            SearchContext context) {
        String clazzName = getClassNameText();
        if (clazzName == null)
            return;
        Collection<LuaClassMethodDef> list = LuaClassMethodIndex.findStaticMethods(clazzName, context);
        for (LuaClassMethodDef def : list) {
            String methodName = def.getName();
            if (methodName != null && completionResultSet.getPrefixMatcher().prefixMatches(methodName)) {
                LookupElementBuilder elementBuilder = LookupElementBuilder.create(methodName)
                        .withIcon(LuaIcons.CLASS_METHOD)
                        .withInsertHandler(new FuncInsertHandler(def.getFuncBody()))
                        .withTypeText(clazzName)
                        .withItemTextUnderlined(true);
                if (bold)
                    elementBuilder = elementBuilder.bold();

                completionResultSet.addElement(elementBuilder);
            }
        }

        LuaType superType = getSuperClass(context);
        if (superType != null)
            superType.addStaticMethodCompletions(completionResultSet, false, context);
    }

    public void addFieldCompletions(@NotNull CompletionParameters completionParameters,
                                    @NotNull CompletionResultSet completionResultSet,
                                    SearchContext context) {
        Project project = completionParameters.getEditor().getProject();
        assert project != null;

        addFieldCompletions(completionParameters, completionResultSet, project, true, true, context);
        addStaticMethodCompletions(completionResultSet, true, context);
        addMethodCompletions(completionParameters, completionResultSet, true);
    }

    protected void addFieldCompletions(@NotNull CompletionParameters completionParameters,
                                       @NotNull CompletionResultSet completionResultSet,
                                       @NotNull Project project,
                                       boolean bold,
                                       boolean withSuper,
                                       SearchContext context) {
        String clazzName = getClassNameText();
        if (clazzName == null)
            return;

        Collection<LuaClassField> list = LuaClassFieldIndex.getInstance().get(clazzName, project, new ProjectAndLibrariesScope(project));

        for (LuaClassField fieldName : list) {
            String name = fieldName.getFieldName();
            if (name == null)
                continue;

            LookupElementBuilder elementBuilder = LookupElementBuilder.create(name)
                    .withIcon(LuaIcons.CLASS_FIELD)
                    .withTypeText(clazzName);
            if (bold)
                elementBuilder = elementBuilder.bold();

            completionResultSet.addElement(elementBuilder);
        }

        // super
        if (withSuper) {
            LuaType superType = getSuperClass(context);
            if (superType != null)
                superType.addFieldCompletions(completionParameters, completionResultSet, project, false, true, context);
        }
    }

    public LuaTypeSet guessFieldType(String propName, SearchContext context) {
        LuaTypeSet set = null;
        LuaClassField fieldDef = LuaClassFieldIndex.find(getClassNameText(), propName, context);
        if (fieldDef != null)
            set = fieldDef.guessType(context);
        else {
            LuaType superType = getSuperClass(context);
            if (superType != null)
                set = superType.guessFieldType(propName, context);
        }

        return set;
    }

    public LuaClassField findField(String fieldName, SearchContext context) {
        String className = getClassNameText();
        LuaClassField def = LuaClassFieldIndex.find(className, fieldName, context);
        if (def == null) {
            LuaType superType = getSuperClass(context);
            if (superType != null)
                def = superType.findField(fieldName, context);
        }
        return def;
    }

    public LuaClassMethodDef findMethod(String methodName, SearchContext context) {
        String className = getClassNameText();
        LuaClassMethodDef def = LuaClassMethodIndex.findMethodWithName(className, methodName, context);
        if (def == null) { // static
            def = LuaClassMethodIndex.findStaticMethod(className, methodName, context);
        }
        if (def == null) { // super
            LuaType superType = getSuperClass(context);
            if (superType != null)
                def = superType.findMethod(methodName, context);
        }
        return def;
    }

    /*private LuaClassMethodDef findStaticMethod(String methodName, @NotNull SearchContext context) {
        String className = getClassNameText();
        LuaClassMethodDef def = LuaClassMethodIndex.findStaticMethod(className, methodName, context);
        if (def == null) {
            LuaType superType = getSuperClass(context);
            if (superType != null)
                def = superType.findStaticMethod(methodName, context);
        }
        return def;
    }*/
}
