/*
 * @test
 * @summary Test class loader register in EagerCDS
 * @library /lib/testlibrary /test/lib
 * @modules jdk.compiler
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.access
 * @modules java.base/com.alibaba.util:+open
 * @build jdk.test.lib.compiler.CompilerUtils
 * @requires os.arch=="amd64"
 * @run main/othervm TestRegisterClassLoader
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.internal.misc.JavaLangClassLoaderAccess;
import jdk.internal.access.SharedSecrets;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.compiler.CompilerUtils;

public class TestRegisterClassLoader {
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String SRC_FILENAME = "TestRegisterClassLoader.java";

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path CLASSES_DIR = Paths.get("classes");
    private static final String THROW_EXCEPTION_CLASS = "p.ThrowException";

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                        "-Dtest.src=" + TEST_SRC,
                        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                        "--add-exports=java.base/com.alibaba.util=ALL-UNNAMED",
                        TestRegisterClassLoader.class.getName(), "1");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("[Register CL Exception] identifier or loader is null")
                  .shouldContain("[Register CL Exception] the identifier myloader with signature:");
            return;
        }
        /*
         * Test Register class loader for a class loaded by a named URLClassLoader
         */
        compile();

        //  class loader with name
        testURLClassLoader("myloader");

        //  class loader without name
        testURLClassLoader(null);

        //  class loader with duplicate name
        testURLClassLoader("myloader");
    }

    private static void compile() throws Exception {
        System.out.println(SRC_DIR + " in " + CLASSES_DIR);
        boolean rc = CompilerUtils.compile(SRC_DIR, CLASSES_DIR);
        if (!rc) {
            throw new RuntimeException("compilation fails" + SRC_DIR + " in " + CLASSES_DIR);
        }
    }

    public static void testURLClassLoader(String loaderName) throws Exception {
        System.err.println("---- test URLClassLoader name: " + loaderName);

        URL[] urls = new URL[] { CLASSES_DIR.toUri().toURL() };
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        URLClassLoader loader = new URLClassLoader(urls, parent);

        try {
            Method m = Class.forName("com.alibaba.util.Utils").getDeclaredMethod("registerClassLoader",
                                     ClassLoader.class, String.class);
            m.setAccessible(true);
            m.invoke(null, loader, loaderName);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        Class<?> c = Class.forName(THROW_EXCEPTION_CLASS, true, loader);
        Method method = c.getMethod("throwError");
        try {
            // invoke p.ThrowException::throwError
            method.invoke(null);
        } catch (InvocationTargetException x) {
        }
    }

}

