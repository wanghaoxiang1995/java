/**
 * Created by Administrator on 2017/7/3 0003.
 */
public class ClassTest {
    public static void main(String[] args) {
        try {
            Class.forName("TestBO");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        TestBO testBO = new TestBO();
        testBO = null;
        System.out.println(testBO);
        System.gc();
        testBO = new TestBO();
        System.out.println(testBO);
    }
}
