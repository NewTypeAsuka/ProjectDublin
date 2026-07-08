import org.junit.jupiter.api.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class JUnitCycleQuiz {

    @BeforeEach
    public void beforeEach() {
        System.out.println("Hello!");
    }

    @Test
    public void junitQuiz3() {
        System.out.println("This is a first test");
    }

    @Test
    public void junitQuiz4() {
        System.out.println("This is a second test");
    }

    @AfterAll
    public static void afterAll() {
        System.out.println("Goodbye!");
    }

}
