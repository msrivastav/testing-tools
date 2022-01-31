package me.msri.annotation.processor;

import com.sun.source.util.Trees;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import lombok.extern.slf4j.Slf4j;
import me.msri.annotation.BuildImage;
import me.msri.annotation.buildtool.BuildToolRunner;
import me.msri.annotation.buildtool.BuildToolRunnerProvider;

@SupportedAnnotationTypes("me.msri.annotation.BuildImage")
@Slf4j
public class BuildImageProcessor extends AbstractProcessor {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");

  private Trees trees;

  private static final BuildToolRunner BUILD_TOOL_RUNNER = BuildToolRunnerProvider.getBuildToolRunner();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    trees = Trees.instance(processingEnv);
  }
  // 1. It is gradle project or maven
  // 2. List services managed by gradle
  // 3. Is it > SB 2.3+. Is it < SB 2.3. Is it executable jar. Non Exec jar
  // 3. Image already exists
  // 5. Detect code change and create new image
  // 6. Create Image
  // 7. Delete image after test
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    /*      Path root = FileSystems.getDefault().getPath("").toAbsolutePath();
    Path filePath = Paths.get(root.toString(),"src", "main", "resources", "SpringBootAppTest");
    System.out.println(root);
    System.out.println(filePath);*/

    /*      System.out.println("---");
    roundEnv.getElementsAnnotatedWith(BuildImage.class).stream().map(this::getFileOrObject)
        .forEach(System.out::println);
    System.out.println("---");
    final var f = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
    System.out.println(">>>> " + f);
    System.out.println("---");*/

    roundEnv.getElementsAnnotatedWith(BuildImage.class).stream()
        .filter(TypeElement.class::isInstance)
        .map(this::getAbsolutePathOfAnnotatedClass)
        .forEach(System.out::println);
    return true;
  }

  private String getAbsolutePathOfAnnotatedClass(final Element classOrInterfaceType) {
    return trees
        .getPath(classOrInterfaceType)
        .getCompilationUnit()
        .getSourceFile()
        .toUri()
        .getPath();
  }
}
