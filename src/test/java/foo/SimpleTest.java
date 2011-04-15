package foo;

import org.junit.Rule;
import org.junit.Test;

import jenkins.test.JenkinsMain;

public class SimpleTest {
    @Rule public final JenkinsMain jenkins = new JenkinsMain();

    @Test
    public void a() throws Throwable {
        jenkins.start();
        System.out.println(jenkins.baseUrl());
        Thread.sleep(1000);
    }
    
    @Test 
    public void b() {
    }
}
