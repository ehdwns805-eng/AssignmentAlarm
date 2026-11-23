// 파일 읽기
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

// 목록과 키보드 입력
import java.util.ArrayList;
import java.util.Scanner;

// 입력 보관과 시간 계산
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// 알람 소리 재생
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Clip;


public class MainAlarm {

    // ============================================================
    // 공통 변수 — 여러 메서드에서 함께 사용
    // ============================================================

    static Scanner sc = new Scanner(System.in); // 키보드 입력 도구
    static Clip alarmClip; // 반복 재생 중인 알람, 없으면 null

    static ArrayList<String> munjaList = new ArrayList<>(); // 문제 목록
    static ArrayList<String> dapList = new ArrayList<>();   // 정답 목록
    static ArrayList<String> typeList = new ArrayList<>();  // 유형 목록: 1 단답식, 2 OX, 3 객관식

    // 전체 기록 안에 각 회차의 답안 목록을 저장
    static ArrayList<ArrayList<String>> recordList = new ArrayList<>();

    // 키보드로 입력한 내용을 잠시 보관
    static LinkedBlockingQueue<String> inputs = new LinkedBlockingQueue<>();


    // ============================================================
    // 프로그램 시작 — 기본 문제 등록과 메인 메뉴 연결
    // ============================================================

    public static void main(String[] args) throws InterruptedException {
        munjaList.add("1 + 1 = ?"); // 기본 문제 등록
        dapList.add("2");           // 같은 위치에 기본 정답 등록
        typeList.add("1");         // 기본 문제는 단답식

        prepareInput(); // 키보드 입력을 담당할 스레드 실행

        while (true) {
            String menu = mainMenu(); // 메인 메뉴를 보여주고 번호 입력

            if (menu.equals("1")) {
                start(); // 학습 시작
            } else if (menu.equals("2")) {
                manageQuestions(); // 문제 관리
            } else if (menu.equals("3")) {
                showRecords(); // 학습 기록 확인
            } else if (menu.equals("0")) {
                exit(); // 종료 안내
                break;  // 메인 메뉴 반복 종료
            } else {
                System.out.println("메뉴 번호를 다시 입력하세요.");
            }
        }
    }


    // ============================================================
    // 메인 메뉴
    // ============================================================

    static String mainMenu() {
        System.out.println("\n=== 메인 메뉴 ===");
        System.out.println("1. 시작");
        System.out.println("2. 문제 출제");
        System.out.println("3. 기록");
        System.out.println("0. 종료");
        System.out.print("선택: ");

        return readLine(); // 입력한 번호를 main()으로 전달
    }


    // ============================================================
    // 1. 시작 — 시간 설정, 문제 출제, 채점, 답안 기록
    // ============================================================

    static void start() throws InterruptedException {

        // --------------------------------------------------------
        // 1-1. 등록된 문제가 있는지 확인
        // --------------------------------------------------------

        if (munjaList.isEmpty()) {
            System.out.println("먼저 문제를 등록해 주세요.");
            return;
        }

        // 선택한 유형의 문제 index만 모아서 출제에 사용
        System.out.println("출제 형식을 골라주세요.");
        System.out.println("1. 전체");
        System.out.println("2. 단답식");
        System.out.println("3. OX 방식");
        System.out.println("4. 객관식");
        System.out.println("5. 뒤로가기");
        System.out.print("선택: ");
        String type = readLine().trim(); // 출제할 유형 입력

        if (type.equals("5") || type.equals("---")) {
            return; // 메인 메뉴로 돌아가기
        }

        if (!type.equals("1") && !type.equals("2") && !type.equals("3") && !type.equals("4")) {
            System.out.println("1~5 중에서 입력해 주세요.");
            return;
        }

        String selectedType = String.valueOf(Integer.parseInt(type) - 1); // 시작 메뉴 2~4를 저장된 유형 1~3으로 변환
        ArrayList<Integer> indexes = new ArrayList<>(); // 선택한 유형의 원래 index
        for (int i = 0; i < typeList.size(); i++) { // 모든 문제의 유형 확인
            if (type.equals("1") || typeList.get(i).equals(selectedType)) { // 전체이거나 선택한 유형과 같으면
                indexes.add(i); // 해당 문제의 index 저장
            }
        }

        if (indexes.isEmpty()) { // 해당 유형의 문제가 없으면
            System.out.println("선택한 유형의 문제가 없습니다. 먼저 등록해 주세요.");
            return;
        }

        // --------------------------------------------------------
        // 1-2. 출제 간격 입력
        // --------------------------------------------------------

        System.out.print("몇 분 간격으로 출제할까요? (---: 뒤로가기) ");
        String intervalInput = readLine();

        if (intervalInput.equals("---")) {
            return; // 숫자로 바꾸기 전에 뒤로가기 처리
        }

        int interval = Integer.parseInt(intervalInput); // 글자를 정수로 변환

        // --------------------------------------------------------
        // 1-3. 총 진행 시간 입력
        // --------------------------------------------------------

        System.out.print("총 몇 분 동안 진행할까요? (---: 뒤로가기) ");
        String durationInput = readLine();

        if (durationInput.equals("---")) {
            return;
        }

        int duration = Integer.parseInt(durationInput);

        if (duration <= interval) {
            System.out.println("간격보다 총 분이 커야 합니다.");
            return;
        }

        if (interval <= 0 || duration <= 0) {
            System.out.println("시간은 1 이상의 정수로 입력해 주세요.");
            return;
        }

        // --------------------------------------------------------
        // 1-4. 출제 방식 선택
        // --------------------------------------------------------

        System.out.println("문제 출제 방식을 선택해 주세요. (---: 뒤로가기)");
        System.out.println("1. 랜덤");
        System.out.println("2. 순서대로");
        System.out.print("선택: ");
        String order = readLine();

        if (order.equals("---")) {
            return;
        }

        if (!order.equals("1") && !order.equals("2")) {
            System.out.println("1 또는 2를 입력하세요.");
            return;
        }

        // --------------------------------------------------------
        // 1-5. 시간과 문제 상태 준비
        // --------------------------------------------------------

        long intervalTime = TimeUnit.MINUTES.toNanos(interval); // 출제 간격
        long totalTime = TimeUnit.MINUTES.toNanos(duration)
                + TimeUnit.SECONDS.toNanos(1); // 총 시간에 마지막 출제 여유 1초 추가

        long startTime = System.nanoTime(); // 학습 시작 시각
        long nextTime = intervalTime;       // 첫 문제를 출제할 시점

        int questionNumber = -1;   // -1이면 현재 풀고 있는 문제가 없음
        int nextQuestionIndex = 0; // 순서대로 출제할 다음 문제 번호

        // --------------------------------------------------------
        // 1-6. 이번 회차의 기록과 정답률 집계 준비
        // --------------------------------------------------------

        ArrayList<String> roundRecords = new ArrayList<>(); // 이번 회차 답안 목록
        recordList.add(roundRecords); // 전체 기록에 이번 회차 목록 연결

        int submittedCount = 0; // 이번 회차의 답 제출 횟수
        int correctCount = 0;   // 이번 회차의 정답 횟수

        System.out.println("대기중입니다...");
        System.out.println("문제가 어려우면 \"---\"를 입력해 오답 처리하고 다음 출제를 기다릴 수 있습니다.");

        // --------------------------------------------------------
        // 1-7. 시간 확인 → 문제 출제 → 답 입력 확인을 반복
        // --------------------------------------------------------

        try {
            while (true) {
                long elapsed = System.nanoTime() - startTime; // 시작 후 흐른 시간

                // 시간이 끝났고 풀고 있는 문제도 없으면 학습 종료
                if (elapsed >= totalTime && questionNumber == -1) {
                    System.out.println("\n종료되었습니다.");
                    showAccuracy(submittedCount, correctCount); // 이번 회차 정답률 출력
                    return;
                }

                // ----------------------------------------------------
                // 출제 시각이 되었고 총 진행 시간이 남아 있으면
                // ----------------------------------------------------

                if (elapsed >= nextTime && elapsed < totalTime) {
                    if (questionNumber == -1) {

                        if (order.equals("1")) {
                            // 랜덤으로 문제 번호 선택
                            questionNumber = indexes.get((int) (Math.random() * indexes.size())); // 해당 유형에서 랜덤 선택

                        } else {
                            // 다음 문제 번호를 사용하고 그다음 번호 준비
                            questionNumber = indexes.get(nextQuestionIndex); // 해당 유형의 원래 문제 index
                            nextQuestionIndex++;

                            if (nextQuestionIndex == indexes.size()) {
                                nextQuestionIndex = 0; // 마지막 문제 다음에는 첫 문제
                            }
                        }

                        System.out.println("\n문제: " + munjaList.get(questionNumber));
                        System.out.print("답: ");
                        playAlarm(); // 새 문제가 나왔을 때 알람 재생

                    } else {
                        System.out.println("\n문제를 풀고 있어 이번 출제는 건너뜁니다.");
                        System.out.print("답: ");
                    }

                    // 시작 시각을 기준으로 다음 출제 시점 계산
                    nextTime = (elapsed / intervalTime + 1) * intervalTime;
                }

                // ----------------------------------------------------
                // 입력 확인 — 입력이 없으면 null을 받고 계속 진행
                // ----------------------------------------------------

                String answer = inputs.poll();

                if (answer != null) {
                    if (answer.equals("---") && questionNumber == -1) {
                        System.out.println("메인 메뉴로 돌아갑니다.");
                        return;
                    }

                    // 현재 문제가 있을 때만 답을 채점
                    if (questionNumber != -1) {

                        // ------------------------------------------------
                        // 채점과 이번 회차 횟수 집계
                        // ------------------------------------------------

                        String correctAnswer = dapList.get(questionNumber);
                        boolean isSkipped = answer.trim().equals("---"); // 풀이 중 --- 입력은 스킵
                        boolean isCorrect = !isSkipped && answer.trim().equals(correctAnswer.trim()); // 스킵은 항상 오답

                        submittedCount++; // 제출할 때마다 1 증가

                        String result = "오답";

                        if (isCorrect) {
                            correctCount++; // 정답일 때만 1 증가
                            result = "정답";
                        }

                        // ------------------------------------------------
                        // 제출한 답 한 건을 문자열로 만들어 기록
                        // ------------------------------------------------

                        String record = "문제: " + munjaList.get(questionNumber)
                                + "\n문제 정답: " + correctAnswer
                                + "\n출제자 정답: " + (isSkipped ? "--- (스킵)" : answer)
                                + "\n결과: " + result;

                        roundRecords.add(record); // 이번 회차 답안 목록에 추가

                        // ------------------------------------------------
                        // 채점 결과 출력과 현재 문제 상태 변경
                        // ------------------------------------------------

                        if (isSkipped) {
                            stopAlarm(); // 스킵하면 알람 종료
                            System.out.println("문제를 오답 처리하고 넘어갑니다.");
                            System.out.println("대기중입니다...");
                            System.out.println("문제가 어려우면 \"---\"를 입력해 오답 처리하고 다음 출제를 기다릴 수 있습니다.");
                            questionNumber = -1; // 현재 풀이 종료, 기존 출제 간격에 맞춰 다음 문제 대기
                        } else if (isCorrect) {
                            stopAlarm(); // 정답을 맞히면 알람 종료
                            System.out.println("정답입니다!");
                            System.out.println("대기중입니다...");
                            System.out.println("문제가 어려우면 \"---\"를 입력해 오답 처리하고 다음 출제를 기다릴 수 있습니다.");
                            questionNumber = -1; // 풀이 완료

                        } else {
                            System.out.println("땡! 틀렸습니다.");
                            System.out.print("답: "); // 같은 문제에 다시 답 입력
                        }
                    }
                }

                Thread.sleep(50); // 0.05초 쉬고 시간과 입력을 다시 확인
            }
        } finally {
            stopAlarm(); // 학습 종료, 뒤로가기, 예외 발생 시에도 알람 종료
        }
    }


    // ============================================================
    // 2. 문제 출제 — 등록, 가져오기, 확인 메뉴
    // ============================================================

    static void manageQuestions() {
    while (true) {
        System.out.println("\n=== 문제 출제 ===");
        System.out.println("1. 문제 등록");
        System.out.println("2. 문제 삭제");
        System.out.println("3. 문제 가져오기");
        System.out.println("4. 문제 확인");
        System.out.println("5. 뒤로 가기");
        System.out.print("선택: ");
        String choice = readLine().trim(); // 메뉴 번호 입력

        if (choice.equals("1")) {
            registerQuestion(); // 문제 등록
        } else if (choice.equals("2")) {
            deleteQuestion(); // 문제 삭제
        } else if (choice.equals("3")) {
            importQuestionMenu(); // 파일에서 가져오기
        } else if (choice.equals("4")) {
            showQuestions(); // 전체 문제 확인
        } else if (choice.equals("5")) {
            return; // 메인 메뉴로 돌아가기
        } else {
            System.out.println("1~5 중에서 입력해 주세요.");
        }
    }
}


    // ------------------------------------------------------------
    // 2-1. 문제 등록 — 키보드로 문제와 정답 직접 입력
    // ------------------------------------------------------------
       
    static void registerQuestion() {
    System.out.println("1. 단답형");//1
    System.out.println("2. OX퀴즈");
    System.out.println("3. 객관식"); 
    String menu2 = readLine().trim(); // 2: 유형 입력

    if (menu2.equals("1") || menu2.equals("2") || menu2.equals("3")) {  // 3
        System.out.println("문제를 입력하세요.");
        System.out.println("입력 완료 시 ---만 입력하세요.");

        StringBuilder text = new StringBuilder(); // 4: 여러 줄을 모을 공간

        while (true) { // 5: 문제 입력 반복
            String line = readLine(); // 6: 한 줄 입력

            if (line.equals("---")) { // 7: 문제 입력 완료
                break;
            }

            text.append(line).append("\n"); // 8: 입력한 줄과 줄바꿈 저장
        }

        // 9: 마지막에 자동으로 붙인 줄바꿈 하나만 제거
        if (text.length() > 0) {
            text.setLength(text.length() - 1);
        }

        String munja = text.toString(); // 10: 모은 내용을 문자열로 변환

        System.out.println("답을 입력해주세요."); // 11
        String dap = readLine(); // 12: 정답 한 줄 입력

        munjaList.add(munja); // 13: 문제 저장
        dapList.add(dap);     // 14: 정답 저장
        typeList.add(menu2);  // 같은 위치에 선택한 유형 저장

        System.out.println("문제를 등록했습니다."); // 15
    } else {
        System.out.println("1~3 중에서 입력해 주세요.");
    }
}

    // ------------------------------------------------------------
    // 문제 삭제 — 전체 문제를 보여주고 선택한 문제와 정답 삭제
    // ------------------------------------------------------------

    static void deleteQuestion() {
        System.out.println("\n=== 문제 삭제 ===");

        if (munjaList.isEmpty()) { // 등록된 문제가 없으면
            System.out.println("삭제할 문제가 없습니다.");
            return;
        }

        showQuestions(); // 전체 문제와 정답을 번호와 함께 출력

        System.out.print("삭제할 문제 번호를 입력하세요: ");
        String input = readLine().trim(); // 삭제할 번호 입력

        try {
            int number = Integer.parseInt(input); // 입력한 글자를 숫자로 변환

            if (number < 1 || number > munjaList.size()) { // 존재하는 번호인지 확인
                System.out.println("목록에 있는 문제 번호를 입력해 주세요.");
                return;
            }

            int index = number - 1; // 화면의 번호를 실제 목록 위치로 변환

            munjaList.remove(index); // 선택한 문제 삭제
            dapList.remove(index);   // 같은 위치의 정답도 함께 삭제
            typeList.remove(index);  // 같은 위치의 유형도 함께 삭제

            System.out.println(number + "번 문제를 삭제했습니다.");

        } catch (NumberFormatException e) { // 숫자로 바꿀 수 없는 입력이면
            System.out.println("숫자로 입력해 주세요.");
        }
    }


    // ------------------------------------------------------------
    // 2-2. 문제 가져오기 — 파일 불러오기와 양식 안내 메뉴
    // ------------------------------------------------------------

    static void importQuestionMenu() {
        System.out.println("\n=== 문제 가져오기 ===");
        System.out.println("프로그램을 실행하는 폴더에 quiz.txt를 넣어 주세요.");

        while (true) {
            System.out.println("1. 불러와서 문제 등록");
            System.out.println("2. 문제 양식 보기");
            System.out.println("3. 뒤로 가기");
            System.out.print("선택: ");
            String choice = readLine().trim();

            if (choice.equals("1")) {
                importQuestions(); // 파일 내용을 읽어서 등록
                return;

            } else if (choice.equals("2")) {
                System.out.println("|로 항목을 구분하고, 줄을 바꿔 다음 문제를 작성합니다.");
                System.out.println("첫 칸은 문제 유형, 두 번째 칸은 문제, 세 번째 칸은 답입니다.");
                System.out.println("문제 유형은 단답형, OX퀴즈, 객관식 중에서 입력하세요.");
                System.out.println("| 사이에 입력한 문제와 답의 공백은 그대로 저장됩니다.");
                System.out.println("줄 끝은 |, 또는 |로 마무리해도 됩니다.");
                System.out.println("\\n은 다음 줄로 넘어가는 표시입니다.");
                System.out.println("객관식은 문제와 보기 사이에 \\n을 넣고, 답에는 정답 번호를 입력하세요.");

                System.out.println("\n단답형 예시");
                System.out.println("|단답형|1+2=?|3|,");

                System.out.println("\nOX퀴즈 예시");
                System.out.println("|OX퀴즈|2+3=5|O|,");

                System.out.println("\n객관식 예시");
                System.out.println("|객관식|1+1=?\\n1. 1\\n2. 2\\n3. 3\\n4. 4|2|,");
            } else if (choice.equals("3")) {
                return; // 문제 출제 메뉴로 돌아감

            } else {
                System.out.println("1~3 중에서 입력해 주세요.");
            }
        }
    }


    // ------------------------------------------------------------
    // 2-2-1. quiz.txt 읽기 — 한 줄을 문제 하나로 등록
    // ------------------------------------------------------------

    static void importQuestions() {
        int added = 0;      // 등록에 성공한 문제 수
        int lineNumber = 0; // 현재 읽고 있는 줄 번호

        try {
            for (String line : Files.readAllLines(Paths.get("quiz.txt"))) {
                lineNumber++;
                line = line.trim(); // 줄 바깥쪽 공백 정리

                // 메모장에서 UTF-8로 저장할 때 붙을 수 있는 BOM 제거
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }

                if (line.isEmpty()) {
                    continue; // 빈 줄 건너뛰기
                }

                // 줄 끝의 쉼표가 있으면 제거
                if (line.endsWith(",")) {
                    line = line.substring(0, line.length() - 1).trim();
                }

                // |로 구분하고, 맨 앞과 맨 뒤의 빈 칸도 유지
                String[] parts = line.split("\\|", -1);

                // 정상 양식이면 빈 칸, 유형, 문제, 답, 빈 칸의 총 5칸
                if (parts.length != 5
                        || !parts[0].isEmpty()
                        || !parts[4].isEmpty()) {

                    System.out.println(lineNumber + "번째 줄: 양식을 확인해 주세요.");
                    continue;
                }

                String type = parts[1].trim(); // 문제 유형
                String question = parts[2].replace("\\n", "\n"); // 적어 둔 \n을 실제 줄바꿈으로 변환 문제의 공백 유지
                String answer = parts[3];     // 정답의 공백 유지

                // 유형이 올바르고 문제와 답이 있는지 확인
                if ((!type.equals("단답형")
                && !type.equals("OX퀴즈")
                && !type.equals("객관식"))
                || question.trim().isEmpty()
                || answer.trim().isEmpty()) {

            System.out.println(lineNumber + "번째 줄: 유형, 문제, 답을 확인해 주세요.");
            continue;
        }

                munjaList.add(question); // 문제 목록에 추가
                dapList.add(answer);     // 같은 위치에 정답 추가
                // 파일의 유형 이름을 메뉴 번호로 바꿔 같은 위치에 저장
                if (type.equals("단답형")) {
                    typeList.add("1");
                } else if (type.equals("OX퀴즈")) {
                    typeList.add("2");
                } else {
                    typeList.add("3");
                }
                added++;
            }

            System.out.println(added + "개의 문제를 추가했습니다.");

        } catch (IOException e) {
            System.out.println(
                    "quiz.txt를 읽을 수 없습니다. 파일 이름과 UTF-8 저장 여부를 확인해 주세요.");
        }
    }


    // ------------------------------------------------------------
    // 2-3. 문제 확인 — 등록된 모든 문제와 정답 출력
    // ------------------------------------------------------------

    static void showQuestions() {
        for (int i = 0; i < munjaList.size(); i++) {
            System.out.println((i + 1) + ". 문제: " + munjaList.get(i));
            System.out.println("   정답: " + dapList.get(i));
        }
    }


    // ============================================================
    // 3. 기록 — 회차별 답안, 회차별 정답률, 전체 정답률
    // ============================================================

    static void showRecords() {
        System.out.println("\n=== 결과 ===");

        if (recordList.isEmpty()) {
            System.out.println("아직 학습 기록이 없습니다.");
            return;
        }

        int totalSubmitted = 0; // 모든 회차의 제출 횟수
        int totalCorrect = 0;   // 모든 회차의 정답 횟수

        for (int i = 0; i < recordList.size(); i++) {
            int roundNumber = i + 1;
            System.out.println("-" + roundNumber + "회차-");

            ArrayList<String> roundRecords = recordList.get(i);
            int roundSubmitted = roundRecords.size(); // 이번 회차 제출 횟수
            int roundCorrect = 0; // 회차가 바뀔 때마다 0부터 집계

            // 이번 회차의 답안 기록을 출력하면서 정답 개수 확인
            for (int j = 0; j < roundSubmitted; j++) {
                String record = roundRecords.get(j);
                System.out.println((j + 1) + ". " + record);

                if (record.endsWith("\n결과: 정답")) {
                    roundCorrect++;
                }
            }

            // 이번 회차 정답률 출력
            System.out.print(roundNumber + "회차 ");
            showAccuracy(roundSubmitted, roundCorrect);

            // 이번 회차의 횟수를 전체 합계에 추가
            totalSubmitted += roundSubmitted;
            totalCorrect += roundCorrect;
            System.out.println();
        }

        // 모든 회차를 확인한 다음 전체 정답률 출력
        if (totalSubmitted > 0) {
            System.out.print("전체 ");
            showAccuracy(totalSubmitted, totalCorrect);

        } else {
            System.out.println("전체 정답률: 제출한 답이 없습니다.");
        }
    }


    // ------------------------------------------------------------
    // 정답률 계산 — 학습 종료와 기록 화면에서 함께 사용
    // ------------------------------------------------------------

    static void showAccuracy(int submittedCount, int correctCount) {
        if (submittedCount == 0) {
            System.out.println("제출한 답이 없습니다.");
            return; // 제출 횟수가 0이면 계산 종료
        }

        double accuracy = correctCount * 100.0 / submittedCount;

        System.out.printf("정답률: %.1f%% (%d번 정답 / %d번 제출)%n",
                accuracy, correctCount, submittedCount);
    }


    // ============================================================
    // 0. 종료 — 종료 안내 출력
    // ============================================================

    static void exit() {
        stopAlarm(); // 프로그램 종료 전 재생 장치 정리
        System.out.println("프로그램을 종료합니다.");
    }


    // ============================================================
    // 알람 — 문제를 맞히거나 스킵할 때까지 반복 재생
    // ============================================================

    static void playAlarm() {
        stopAlarm(); // 이전 재생 장치가 남아 있으면 먼저 종료
        int sampleRate = 16000; // 1초에 사용할 소리 데이터 개수
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        int[] notes = {659, 523, 784, 523}; // 딩, 동, 댕, 동
        int samplesPerNote = sampleRate / 5; // 음 하나는 0.2초
        byte[] sound = new byte[samplesPerNote * 2 * notes.length]; // 멜로디 전체 저장

        for (int note = 0; note < notes.length; note++) {
            for (int i = 0; i < samplesPerNote; i++) {
                double time = (double) i / sampleRate;
                double volume = Math.min(1.0, time * 200) * Math.exp(-20 * time);
                short sample = (short) (6000 * volume
                        * Math.sin(2 * Math.PI * notes[note] * time));
                int offset = (note * samplesPerNote + i) * 2; // 현재 음의 저장 위치
                sound[offset] = (byte) sample;
                sound[offset + 1] = (byte) (sample >> 8);
            }
        }

        try {
            alarmClip = AudioSystem.getClip(); // 반복 재생할 장치 준비
            alarmClip.open(format, sound, 0, sound.length); // 멜로디 전체 등록
            alarmClip.loop(Clip.LOOP_CONTINUOUSLY); // 답 입력과 별도로 계속 반복
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            stopAlarm(); // 장치 준비 중 실패해도 자원 정리
            System.err.println("\n알람 소리를 재생할 수 없습니다.");
        }
    }

    static void stopAlarm() {
        if (alarmClip != null) { // 재생 장치가 있을 때만 처리
            alarmClip.stop(); // 재생 즉시 중지
            alarmClip.flush(); // 남아 있는 소리 데이터 비우기
            alarmClip.close(); // 장치 해제
            alarmClip = null; // 다음 문제에서 새로 준비
        }
    }


    // ============================================================
    // 입력 처리 — 키보드 입력을 받아 보관하고 필요한 곳에 전달
    // ============================================================

    // 프로그램 시작 시 한 번 실행하는 키보드 입력 담당
    static void prepareInput() {
        Thread inputThread = new Thread(() -> {
            while (sc.hasNextLine()) { // 다음 줄 입력이 들어올 때까지 대기
                inputs.offer(sc.nextLine()); // 입력한 줄을 보관
            }
        });

        inputThread.setDaemon(true); // 프로그램 종료와 함께 종료
        inputThread.start(); // 입력 담당 실행
    }


    // 메뉴와 문제 등록에서 입력을 기다린 뒤 한 줄 가져오기
    static String readLine() {
        try {
            return inputs.take(); // 입력이 들어올 때까지 기다렸다가 반환

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 중단 상태 복원
            throw new IllegalStateException(e); // 호출한 곳에 예외 전달
        }
    }
}
