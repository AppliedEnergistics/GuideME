package guideme;

import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Tests that every class we consider API does not expose classes we do NOT consider API.
 */
public class ApiConsistencyTest {

    private static final Class<?> CANARY_CLASS = Guide.class;

    @TestFactory
    public List<DynamicNode> testApiConsistency() throws Exception {

        // Find classes root for the canary class
        var classRoot = getClassRoot();

        var result = new ArrayList<DynamicNode>();
        Files.walkFileTree(classRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".class")) {
                    String classFilePath = classRoot.relativize(file).toString().replace('\\', '/');
                    if (!isApiClassFile(classFilePath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    result.add(DynamicTest.dynamicTest(
                            classFilePath.replace('/', '.').replaceAll(".class$", ""),
                            () -> testApiConsistency(classRoot, file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private void testApiConsistency(Path classRoot, Path file) throws Exception {
        var violations = new ArrayList<String>();

        String[] className = new String[1];

        var visitor = new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                className[0] = name;

                if (isForbiddenTypeReference(superName)) {
                    violations.add("Non-API super-class: " + superName);
                }

                for (var ifType : interfaces) {
                    if (isForbiddenTypeReference(ifType)) {
                        violations.add("Implements non-API interface: " + ifType);
                    }
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & (Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) != 0) {
                    var fieldType = Type.getType(descriptor);
                    if (isForbiddenTypeReference(fieldType.getClassName())) {
                        violations.add(
                                "Has visible field with non-API type: " + name + " (" + fieldType.getClassName() + ")");
                    }
                }

                return null;
            }

            @Override
            public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
                var fieldType = Type.getType(descriptor);
                if (isForbiddenTypeReference(fieldType.getClassName())) {
                    violations.add(
                            "Has record component with non-API type: " + name + " (" + fieldType.getClassName() + ")");
                }

                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {

                // Ignore non-public methods
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) {
                    return null;
                }

                for (var argType : Type.getArgumentTypes(descriptor)) {
                    if (isForbiddenTypeReference(argType.getClassName())) {
                        violations.add(
                                "Has method with non-API parameter: " + name + " (" + argType.getClassName() + ")");
                    }
                }

                var returnType = Type.getReturnType(descriptor);
                if (isForbiddenTypeReference(returnType.getClassName())) {
                    violations.add(
                            "Has method with non-API return type: " + name + " (" + returnType.getClassName() + ")");
                }

                if (exceptions != null) {
                    for (String exception : exceptions) {
                        if (isForbiddenTypeReference(exception)) {
                            violations.add("Has method with non-API exception: " + name + " (" + exception + ")");
                        }
                    }
                }

                if (signature != null) {
                    var signatureVisitor = new SignatureVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitClassType(String genericName) {
                            if (isForbiddenTypeReference(genericName)) {
                                violations.add("Has method with non-API generic type parameter: " + name + " ("
                                        + genericName + ")");
                            }
                        }
                    };
                    new SignatureReader(signature).accept(signatureVisitor);
                }

                return null;
            }

            private boolean isForbiddenTypeReference(String type) {
                var classFilename = type.replace('.', '/') + ".class";
                return Files.exists(classRoot.resolve(classFilename)) && !isApiClassFile(classFilename);
            }
        };

        var classData = Files.readAllBytes(file);
        var reader = new ClassReader(classData);
        if ((reader.getAccess() & Opcodes.ACC_PUBLIC) == 0) {
            return; // Ignore non-public classes
        }
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (!violations.isEmpty()) {
            var report = new StringBuilder();
            report.append("Class ").append(className[0].replace('/', '.')).append(" has API violations:\n");
            for (var violation : violations) {
                report.append("  - ").append(violation).append("\n");
            }
            Assertions.fail(report.toString());
        }
    }

    private static boolean isApiClassFile(String classFile) {
        return !classFile.matches(".*/[^/]*Internal\\.[^/]*")
                && !classFile.matches(".*/[^/]Internal\\$[^/]*\\.[^/]*")
                && !classFile.matches(".*/internal/.*");
    }

    private static Path getClassRoot() throws URISyntaxException {
        String canaryClassFilename = CANARY_CLASS.getName().replace('.', '/') + ".class";
        var canaryClassUrl = ClassLoader.getSystemClassLoader().getResource(canaryClassFilename);
        var canaryClassPath = Paths.get(canaryClassUrl.toURI());

        var relativePath = Paths.get(canaryClassFilename);
        if (!canaryClassPath.endsWith(relativePath)) {
            throw new IllegalStateException("Expected " + canaryClassPath + " to end with " + relativePath);
        }

        canaryClassPath = canaryClassPath.getRoot()
                .resolve(canaryClassPath.subpath(0, canaryClassPath.getNameCount() - relativePath.getNameCount()));

        return canaryClassPath;
    }

}
