/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.codeInsight.template.impl.*
import com.intellij.codeInsight.template.macro.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE
/**
 * @author spleaner
 */
public class LiveTemplateTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
    if (state != null) {
      WriteCommandAction.runWriteCommandAction project, {
        state.gotoEnd()
      };
    }
    super.tearDown();
  }

  private void doTestTemplateWithArg(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String fileText,
                                     @NotNull String expected) throws IOException {
    configureFromFileText("dummy.java", fileText);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    String group = "user";
    final Template template = manager.createTemplate(templateName, group, templateText);
    template.addVariable("ARG", "", "", false);
    TemplateContextType contextType = contextType(JavaCodeContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    addTemplate(template, testRootDisposable)

    manager.startTemplate(editor, (char)'\t');
    UIUtil.dispatchAllInvocationEvents()
    checkResultByText(expected);
  }

  public void testTemplateWithSegmentsAtTheSamePosition_1() {
    doTestTemplateWithThreeVariables("", "", "", "class A { void test() { for(TestValue1TestValue2TestValue3) {} } }")
  }

  public void testTemplateWithSegmentsAtTheSamePosition_2() {
    doTestTemplateWithThreeVariables("Def1", "Def2", "DefaultValue", "class A { void test() { for(Def1Def2DefaultValue) {} } }")
  }

  public void testTemplateWithSegmentsAtTheSamePosition_3() {
    doTestTemplateWithThreeVariables("", "DefaultValue", "", "class A { void test() { for(TestValue1DefaultValueTestValue3) {} } }")
  }

  private void doTestTemplateWithThreeVariables(String firstDefaultValue, String secondDefaultValue, String thirdDefaultValue,
                                                String expectedText) {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }")

    TemplateManager manager = TemplateManager.getInstance(getProject())
    def templateName = "tst_template"
    def templateGroup = "user"
    final Template template = manager.createTemplate(templateName, templateGroup, 'for($TEST1$$TEST2$$TEST3$) {}')
    template.addVariable("TEST1", "", StringUtil.wrapWithDoubleQuote(firstDefaultValue), true)
    template.addVariable("TEST2", "", StringUtil.wrapWithDoubleQuote(secondDefaultValue), true)
    template.addVariable("TEST3", "", StringUtil.wrapWithDoubleQuote(thirdDefaultValue), true)
    ((TemplateImpl)template).templateContext.setEnabled(contextType(JavaCodeContextType.class), true)
    addTemplate(template, testRootDisposable)

    startTemplate(templateName, templateGroup)
    UIUtil.dispatchAllInvocationEvents()
    if (firstDefaultValue.empty) myFixture.type("TestValue1")
    myFixture.type("\t")
    if (secondDefaultValue.empty) myFixture.type("TestValue2")
    myFixture.type("\t")
    if (thirdDefaultValue.empty) myFixture.type("TestValue3")
    myFixture.type("\t")
    assert state == null
    checkResultByText(expectedText);
  }

  public void testTemplateWithArg1() throws IOException {
    doTestTemplateWithArg("tst", 'wrap($ARG$)', "tst arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg2() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst#arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg3() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst# arg<caret>", "tst# arg");
  }

  public void testTemplateAtEndOfFile() throws Exception {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", '$VAR$');
    template.addVariable("VAR", "", "", false);

    startTemplate(template);
    checkResultByText("");
  }

  public void testTemplateWithEnd() throws Exception {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", '$VAR$$END$');
    template.addVariable("VAR", "bar", "bar", true);
    template.setToReformat(true);

    startTemplate(template);
    myFixture.type("foo");
    checkResultByText("foo");
  }

  public void testTemplateWithEndOnEmptyLine() throws Exception {
    configureFromFileText("empty.java", "class C {\n" +
                                        "  bar() {\n" +
                                        "    <caret>\n" +
                                        "  }\n" +
                                        "}");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", 'foo()\n' +
                                                                '  $END$\n' +
                                                                'foo()');
    template.setToReformat(true);
    startTemplate(template);
    checkResultByText("class C {\n" +
                      "  bar() {\n" +
                      "      foo()\n" +
                      "              <caret>\n" +
                      "      foo()\n" +
                      "  }\n" +
                      "}");
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text);
  }

  private void configureFromFileText(String name, String text) {
    myFixture.configureByText(name, text);
  }

  public void testEndInTheMiddle() throws Exception {
    configure();
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", "javax.swing.JFrame frame = new javax.swing.JFrame();\n" +
                                                                    '$END$\n' +
                                                                    "frame.setVisible(true);\n" +
                                                                    "frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);\n" +
                                                                    "frame.pack();");
    template.setToShortenLongNames(false);
    template.setToReformat(true);
    startTemplate(template);
    checkResult();
  }

  public void "test honor custom completion caret placement"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    startTemplate(template);
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state.finished
  }

  public void "test cancel template when completion placed caret outside the variable"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret>() }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    startTemplate(template);
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state
  }

  public void "test non-imported classes in className macro"() {
    myFixture.addClass('package bar; public class Bar {}')
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new ClassNameCompleteMacro()), new EmptyNode(), true)
    startTemplate(template);
    assert !state.finished
    assert 'Bar' in myFixture.lookupElementStrings
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "-out.java");
  }

  private void checkResultByFile(String s) {
    myFixture.checkResultByFile(s);
  }

  public void testToar() throws Throwable {
    configure();
    startTemplate("toar", "other")
    state.gotoEnd();
    checkResult();
  }
  
  def startTemplate(String name, char expandKey) {
    myFixture.type(name)
    myFixture.type(expandKey)
  }

  def startTemplate(String name, String group) {
    startTemplate(TemplateSettings.getInstance().getTemplate(name, group));
  }

  def startTemplate(Template template) {
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), template)
    UIUtil.dispatchAllInvocationEvents()
  }

  private static <T extends TemplateContextType> T contextType(Class<T> clazz) {
    ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz)
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testIter() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    state.nextTab();
    ((LookupImpl)LookupManagerImpl.getActiveLookup(getEditor())).finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR);
    checkResult();
  }

  public void testPreferStartMatchesInLookups() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    myFixture.type('ese\n') //for entrySet
    assert myFixture.lookupElementStrings == ['barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry', 'entry']
    myFixture.type('e')
    assert myFixture.lookupElementStrings == ['entry', 'barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry']
    assert LookupManager.getActiveLookup(editor).currentItem.lookupString == 'entry'
  }

  public void testClassNameDotInTemplate() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    configure();
    startTemplate("soutv", "output")
    myFixture.type('File')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  public void testFinishTemplateVariantWithDot() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = true
    configure();
    startTemplate("soutv", "output")
    myFixture.type('fil')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  public void testAllowTypingRandomExpressionsWithLookupOpen() {
    configure();
    startTemplate("iter", "iterations")
    myFixture.type('file.')
    checkResult()
    assert !state.finished
  }

  private TemplateState getState() {
    TemplateManagerImpl.getTemplateState(getEditor())
  }

  public void testIter1() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    state.nextTab();
    checkResult();
  }

  public void _testIterForceBraces() {
    CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    try {
      configure();
      startTemplate("iter", "iterations")
      stripTrailingSpaces();
      checkResult();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
    }
  }

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument();
    document.setStripTrailingSpacesEnabled(true);
    document.stripTrailingSpaces(getProject());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  public void testIterParameterizedInner() {
    configure();
    startTemplate("iter", "iterations")
    stripTrailingSpaces();
    checkResult();
  }

  public void testIterParameterizedInnerInMethod() {
    configure();
    startTemplate("iter", "iterations")
    stripTrailingSpaces();
    checkResult();
  }

  public void testAsListToar() {
    configure();
    startTemplate("toar", "other")
    myFixture.type('\n\t')
    checkResult();
  }

  public void testVarargToar() {
    configure();
    startTemplate("toar", "other")
    checkResult();
  }

  public void testSoutp() {
    configure();
    startTemplate("soutp", "output")
    checkResult();
  }

  public void testJavaStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other");
    assertFalse(isApplicable("class Foo {{ if (a inst<caret>) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst\n a=b; }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ \"<caret>\" }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a.b(); ) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a(); ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
  }

  public void testJavaExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>toar }}", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>toar) }}", template));
    assertFalse(isApplicable("class Foo {{ return (aaa <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
  }

  public void testJavaDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "other");
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    assertTrue(isApplicable("class Foo {} <caret>xxx", template));

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));

    assertTrue(isApplicable("<caret>xxx package foo; class Foo {}", template));
  }

  public void testOtherContext() throws IOException {
    configureFromFileText("a.java", "class Foo { <caret>xxx }");
    assertInstanceOf(
      assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())),
      JavaCodeContextType.Declaration.class);

    configureFromFileText("a.txt", "class Foo { <caret>xxx }");
    assertInstanceOf(
      assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())),
      EverywhereContextType.class);
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    configureFromFileText("a.java", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst);
  }

  @Override
  protected void invokeTestRunnable(@NotNull final Runnable runnable) throws Exception {
    if (name in ["testNavigationActionsDontTerminateTemplate", "testTemplateWithEnd", "testDisappearingVar",
                 "test do replace macro value with empty result",
                 "test do not replace macro value with null result",
                 "test escape string characters in soutv", "test do not replace macro value with empty result"]) {
      runnable.run();
      return;
    }

    writeCommand(runnable)
  }

  private static writeCommand(Runnable runnable) {
    WriteCommandAction.runWriteCommandAction(null, runnable)
  }

  public void testSearchByDescriptionWhenTemplatesListed() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    myFixture.type('array')
    assert 'itar' in myFixture.lookupElementStrings
  }

  public void testListTemplatesSearchesPrefixInDescription() {
    myFixture.configureByText("a.java", "class A { main<caret> }")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings == ['psvm']
  }

  public void testListTemplatesAction() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])

    myFixture.type('it')
    assert myFixture.lookupElementStrings[0].startsWith('it')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]

    myFixture.type('e')
    assert myFixture.lookupElementStrings[0].startsWith('ite')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('\b\b')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco'])
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('xxxxx')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])
    LookupManager.getInstance(project).hideActiveLookup()
  }

  public void testSelectionFromLookupBySpace() {
    myFixture.configureByText("a.java", "class A {{ itc<caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    myFixture.type ' '
    myFixture.checkResult """\
import java.util.Iterator;

class A {{
    for (Iterator <selection>iterator</selection> = collection.iterator(); iterator.hasNext(); ) {
        Object next =  iterator.next();
        \n\
    }
}}"""
  }

  public void testNavigationActionsDontTerminateTemplate() throws Throwable {
    configureFromFileText("a.txt", "")

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", 'Hello $V1$ World $V1$\nHello $V2$ World $V2$\nHello $V3$ World $V3$');
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "", true);
    template.addVariable("V3", "", "", true);
    final Editor editor = getEditor();

    writeCommand { startTemplate(template) }

    final TemplateState state = getState();

    for (int i = 0; i < 3; i++) {
      assertFalse(String.valueOf(i), state.isFinished());
      myFixture.type('H');
      final String docText = editor.getDocument().getText();
      assertTrue(docText, docText.startsWith("Hello H World H\n"));
      final int offset = editor.getCaretModel().getOffset();

      moveCaret(offset + 1);
      moveCaret(offset);

      myFixture.completeBasic()
      myFixture.type(' ');

      assertEquals(offset + 1, editor.getCaretModel().getOffset());
      assertFalse(state.isFinished());

      myFixture.type('\b');
      assertFalse(state.isFinished());
      writeCommand { state.nextTab() }
    }
    assertTrue(state.isFinished());
    checkResultByFile(getTestName(false) + "-out.txt");
  }

  private void moveCaret(final int offset) {
    edt {
      getEditor().getCaretModel().moveToOffset(offset);
    }
  }

  public void testUseDefaultValueForQuickResultCalculation() {
    myFixture.configureByText 'a.txt', '<caret>'

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", '$V1$ var = $V2$;');
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", '"239"', true);

    writeCommand { startTemplate(template) }

    myFixture.checkResult '<caret> var = 239;'

    myFixture.type 'O'
    myFixture.checkResult 'O<caret> var = 239;'

    myFixture.type '\t'
    myFixture.checkResult 'O var = <selection>239</selection>;'
  }

  public void testTemplateExpandingWithSelection() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("tpl", "user", 'expanded');
    final JavaStringContextType contextType = contextType(JavaStringContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);

    myFixture.configureByText("a.java", "class A { void f() { Stri<selection>ng s = \"tpl</selection><caret>\"; } }")

    addTemplate(template, testRootDisposable)
    myFixture.type '\t'
    myFixture.checkResult 'class A { void f() { Stri   "; } }'
  }

  static void addTemplate(Template template, Disposable parentDisposable) {
    def settings = TemplateSettings.getInstance()
    settings.addTemplate(template);
    Disposer.register(parentDisposable, { settings.removeTemplate(template) } as Disposable)
  }

  public void "test expand current live template on no suggestions in lookup"() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.completeBasic()
    assert myFixture.lookup
    myFixture.type("sout")
    assert myFixture.lookup
    assert myFixture.lookupElementStrings == []
    myFixture.type('\t')
    myFixture.checkResult "class Foo {{\n    System.out.println(<caret>);\n}}"
  }

  public void "_test multi-dimensional toar"() {
    myFixture.configureByText "a.java", '''
class Foo {{
  java.util.List<String[]> list;
  String[][] s = toar<caret>
}}'''
    myFixture.type('\t')
    //state.gotoEnd()
    myFixture.checkResult '''
class Foo {{
  java.util.List<String[]> list;
  String[][] s = list.toArray(new String[list.size()][])<caret>
}}'''
  }

  public void "test inner class name"() {
    myFixture.configureByText "a.java", '''
class Outer {
    class Inner {
        void foo() {
            soutm<caret>
        }
    }
}'''
    myFixture.type('\t')
    assert myFixture.editor.document.text.contains("\"Inner.foo")
  }

  public void "test do not strip type argument containing class"() {
    myFixture.configureByText 'a.java', '''
import java.util.*;
class Foo {
  List<Map.Entry<String, Integer>> foo() { 
    <caret> 
  }
}
'''

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", '$T$ result;');
    template.addVariable('T', new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false)
    template.toReformat = true

    startTemplate(template);
    assert myFixture.editor.document.text.contains('List<Map.Entry<String, Integer>> result;')
  }

  public void "test name shadowing"() {
    myFixture.configureByText "a.java", """class LiveTemplateVarSuggestion {
    private Object value;
    public void setValue(Object value, Object value1){
      inn<caret>
    }
}"""
    myFixture.type('\t')
    assert myFixture.lookupElementStrings == ['value', 'value1']
  }

  public void "test invoke surround template by tab"() {
    myFixture.configureByText "a.txt", "B<caret>"
    myFixture.type('\t')
    myFixture.checkResult("{<caret>}")
  }

  public void "test escape string characters in soutv"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
    soutv<caret>
  }
}
"""
    myFixture.type('\t"a"')
    myFixture.checkResult """
class Foo {
  {
      System.out.println("\\"a\\" = " + "a"<caret>);
  }
}
"""
  }

  public void "test stop at SELECTION when invoked surround template by tab"() {
    myFixture.configureByText "a.txt", "<caret>"

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'foo $ARG$ bar $END$ goo $SELECTION$ after');
    template.addVariable("ARG", "", "", true);

    startTemplate(template);
    myFixture.type('arg')
    state.nextTab()
    assert !state
    checkResultByText 'foo arg bar  goo <caret> after';
  }

  public void "test reuse static import"() {
    myFixture.addClass("""package foo; 
public class Bar { 
  public static void someMethod() {}
  public static void someMethod(int a) {}
}""")
    myFixture.configureByText "a.java", """
import static foo.Bar.someMethod;

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)');
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult """
import static foo.Bar.someMethod;

class Foo {
  {
    someMethod(<caret>)
  }
}
"""
  }

  public void "test snakeCase should convert hyphens to underscores"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", '$A$ $B$ c');
    template.addVariable('A', new EmptyNode(), true)

    def macroCallNode = new MacroCallNode(new SnakeCaseMacro())
    macroCallNode.addParameter(new VariableNode('A', null))
    template.addVariable('B', macroCallNode, false)

    myFixture.configureByText "a.txt", "<caret>"
    startTemplate(template);
    myFixture.type('-foo-bar_goo-')
    state.nextTab()
    assert !state
    myFixture.checkResult('-foo-bar_goo- _foo_bar_goo_ c<caret>')
  }

  public void "test use single member static import first"() {
    myFixture.addClass("""package foo;
public class Bar {
  public static void someMethod() {}
  public static void someMethod(int a) {}
}""")
    myFixture.configureByText "a.java", """

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)');
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult """import static foo.Bar.someMethod;

class Foo {
  {
    someMethod(<caret>)
  }
}
"""
  }

  public void "test two static imports"() {
    myFixture.configureByText "a.java", """

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'java.lang.Math.abs(java.lang.Math.PI);');
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult """\
import static java.lang.Math.PI;
import static java.lang.Math.abs;

class Foo {
  {
    abs(PI);<caret>
  }
}
"""
  }

  public void "test do not replace macro value with null result"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", '$VAR1$ $VAR2$ $VAR1$');
    template.addVariable("VAR1", "", "", true)
    template.addVariable("VAR2", new MacroCallNode(new FileNameMacro()), new ConstantNode("default"), true)
    ((TemplateImpl)template).templateContext.setEnabled(contextType(JavaCodeContextType.class), true)
    addTemplate(template, testRootDisposable)

    startTemplate(template);
    myFixture.checkResult """\
class Foo {
  {
    <caret> a.java 
  }
}
"""
    myFixture.type 'test'

    myFixture.checkResult """\
class Foo {
  {
    test<caret> a.java test
  }
}
"""
  }
  
  public void "test do replace macro value with empty result"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", '$VAR1$ $VAR2$');
    template.addVariable("VAR1", "", "", true)
    template.addVariable("VAR2", new MacroCallNode(new MyMirrorMacro("VAR1")), null, true)
    ((TemplateImpl)template).templateContext.setEnabled(contextType(JavaCodeContextType.class), true)
    addTemplate(template, testRootDisposable)

    writeCommand { startTemplate(template); }
    myFixture.checkResult """\
class Foo {
  {
    <caret> 
  }
}
"""
    writeCommand { myFixture.type '42' }
    myFixture.checkResult """\
class Foo {
  {
    42<caret> 42
  }
}
"""

    writeCommand { myFixture.type '\b\b' }
    myFixture.checkResult """\
class Foo {
  {
    <caret> 
  }
}
"""
  }

  private static class MyMirrorMacro extends Macro {
    private final String myVariableName

    MyMirrorMacro(String variableName) {
      this.myVariableName = variableName
    }

    @Override
    String getName() {
      return "mirror"
    }

    @Override
    String getPresentableName() {
      return getName();
    }

    @Override
    Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
      def state = TemplateManagerImpl.getTemplateState(context.editor)
      return state != null ? state.getVariableValue(myVariableName) : null 
    }

    @Override
    Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
      return calculateResult(params, context)
    }
  }

  public void "test multicaret expanding with space"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.SPACE_CHAR
      startTemplate("sout", TemplateSettings.SPACE_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }
    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
    
  }
  
    public void "test multicaret expanding with enter"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.ENTER_CHAR
      startTemplate("sout", TemplateSettings.ENTER_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }
    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
    
  }
  
    public void "test multicaret expanding with tab"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.TAB_CHAR
      startTemplate("sout", TemplateSettings.TAB_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }
      
    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
  }
}
