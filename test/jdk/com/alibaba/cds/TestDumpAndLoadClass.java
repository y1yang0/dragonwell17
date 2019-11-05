/*
 * @test
 * @summary Test dumping with limited metaspace with loading of JVMCI related classes.
 *          VM should not crash but CDS dump will abort upon failure in allocating metaspace.
 * @library /lib/testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.access
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @modules jdk.compiler
 * @modules java.base/com.alibaba.util:+open
 * @build Classes4CDS
 * @build TestSimple
 * @build TestClassLoaderWithSignature
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar test.jar TestClassLoaderWithSignature
 * @run main/othervm -XX:+UnlockExperimentalVMOptions TestDumpAndLoadClass
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class TestDumpAndLoadClass {

    private static final String TESTJAR = "./test.jar";
    private static final String TESTNAME = "TestClassLoaderWithSignature";
    private static final String TESTCLASS = TESTNAME + ".class";

    private static final String CLASSLIST_FILE = "./TestDumpAndLoadClass.classlist";
    private static final String CLASSLIST_FILE_2 = "./TestDumpAndLoadClass.classlist2";
    private static final String ARCHIVE_FILE = "./TestDumpAndLoadClass.jsa";
    private static final String BOOTCLASS = "java.lang.Class";
    private static final String TEST_CLASS = System.getProperty("test.classes"); 

    public static void main(String[] args) throws Exception {

        // dump loaded classes into a classlist file
        dumpLoadedClasses(new String[] { BOOTCLASS, TESTNAME });

        convertClassList();

        // create an archive using the classlist
        dumpArchive();

        // start the java process with shared archive file
        startWithJsa();
    }

    public static List<String> toClassNames(String filename) throws IOException {
        ArrayList<String> classes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
            for (; ; ) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                classes.add(line.replaceAll("/", "."));
            }
        }
        return classes;
    }

    static void dumpLoadedClasses(String[] expectedClasses) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Dtest.classes=" + TEST_CLASS,
            "-XX:DumpLoadedClassList=" + CLASSLIST_FILE,
            // trigger JVMCI runtime init so that JVMCI classes will be
            // included in the classlist
            "-XX:+EagerAppCDS",
            "-XX:-UseSharedSpaces",
            "-cp",
            TESTJAR,
            TESTNAME);

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "dump-loaded-classes")
            .shouldHaveExitValue(0);

        List<String> dumpedClasses = toClassNames(CLASSLIST_FILE);

        for (String clazz : expectedClasses) {
            boolean findString = false;
            for (String s: dumpedClasses) {
                if (s.contains(clazz)) {
                    findString = true;
                    break;
                }
            }
            if (findString == false) {
                throw new RuntimeException(clazz + " missing in " +
                                           CLASSLIST_FILE);
            }
        }
        boolean findString = false;
        for (String s: dumpedClasses) {
            if (s.contains("source: file:")) {
                findString = true;
                break;
            }
        }
        if (findString == false) {
            throw new RuntimeException(" there is no class loaded by customer class loader");
        }
    }

    static void convertClassList() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "Classes4CDS",
            CLASSLIST_FILE,
            CLASSLIST_FILE_2);

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "convert-class-list")
            .shouldHaveExitValue(0);

    }
    static void dumpArchive() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-cp",
            TESTJAR,
            "-Xlog:cds",
            "-XX:+EagerAppCDS",
            "-XX:SharedClassListFile=" + CLASSLIST_FILE_2,
            "-XX:SharedArchiveFile=" + ARCHIVE_FILE,
            "-Xlog:class+eagerappcds=trace",
            "-Xshare:dump",
            "-XX:MetaspaceSize=12M",
            "-XX:MaxMetaspaceSize=12M");

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "dump-archive");
        int exitValue = output.getExitValue();
        if (exitValue == 1) {
            output.shouldContain("Failed allocating metaspace object type");
        } else if (exitValue == 0) {
            output.shouldContain("Loading classes to share");
        } else {
            throw new RuntimeException("Unexpected exit value " + exitValue);
        }
    }

    static void startWithJsa() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Dtest.classes=" + TEST_CLASS,
            "-XX:+EagerAppCDS",
            "-Xshare:on",
            "-XX:SharedArchiveFile=" + ARCHIVE_FILE,
            "-Xlog:class+eagerappcds=trace",
            "-cp",
            TESTJAR,
            TESTNAME);

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "start-with-shared-archive")
            .shouldHaveExitValue(0);
        output.shouldNotContain("[CDS load class Failed");
        output.shouldContain("[CDS load class] Successful loading of class TestSimple with class loader java/net/URLClassLoader");
    }

}
