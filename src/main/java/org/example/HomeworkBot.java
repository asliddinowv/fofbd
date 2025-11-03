package org.example;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HomeworkBot extends TelegramLongPollingBot {


    private static final String BOT_TOKEN = "8493438532:AAH3LnRJQFHwmV4KzIECsbJTIu8zKWcEtx8";
    private static final String BOT_USERNAME = "@pdpedu_bot";
    private static final String CHANNEL_ID = "@pdpedu_check";


    private static final String DB_URL = "jdbc:postgresql://localhost:5432/homework_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "root";


    private Map<Long, UserSession> userSessions = new HashMap<>();


    public static void main(String[] args) {
        try {

            DatabaseManager.initializeDatabase();


            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            HomeworkBot bot = new HomeworkBot();
            botsApi.registerBot(bot);

            System.out.println("✅ Bot muvaffaqiyatli ishga tushdi!");
            System.out.println("📊 Database ulandi!");
            System.out.println("🤖 Bot nomi: " + BOT_USERNAME);
            System.out.println("📢 Kanal: " + CHANNEL_ID);
        } catch (Exception e) {
            System.err.println("❌ Xatolik: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }


    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
            }
        } catch (Exception e) {
            System.err.println("Update qayta ishlashda xatolik: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void handleTextMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        String firstName = update.getMessage().getFrom().getFirstName();

        if (text.equals("/start")) {
            startConversation(chatId, firstName);
        } else if (text.equals("/help")) {
            sendHelpMessage(chatId);
        } else if (text.equals("/stats")) {
            sendUserStatistics(chatId);
        } else if (userSessions.containsKey(chatId)) {
            processUserInput(chatId, text);
        } else {
            sendMessage(chatId, "❌ Iltimos avval /start buyrug'ini yuboring!");
        }
    }


    private void startConversation(Long chatId, String firstName) {
        UserSession session = new UserSession(chatId);
        userSessions.put(chatId, session);

        String welcomeText = String.format(
                "👋 Assalomu alaykum, <b>%s</b>!\n\n" +
                        "🎓 <b>Uy Vazifa Botiga xush kelibsiz!</b>\n\n" +
                        "Bu bot orqali siz:\n" +
                        "✅ Uy vazifalarini topshirasiz\n" +
                        "✅ Nazorat ishlarini topshirasiz\n" +
                        "✅ Baholaringizni olasiz\n\n" +
                        "📝 Iltimos, <b>ism va familiyangizni</b> to'liq kiriting:\n" +
                        "Masalan: <i>Aliyev Vali</i>",
                firstName
        );

        sendHtmlMessage(chatId, welcomeText);
    }


    private void processUserInput(Long chatId, String text) {
        UserSession session = userSessions.get(chatId);

        switch (session.getState()) {
            case WAITING_NAME:
                if (text.trim().length() < 3) {
                    sendMessage(chatId, "❌ Iltimos, to'liq ism va familiyangizni kiriting!");
                    return;
                }
                session.setStudentName(text.trim());
                session.setState(SessionState.SELECTING_CLASS);
                showClassSelection(chatId);
                break;

            case WAITING_DESCRIPTION:
                if (text.trim().length() < 10) {
                    sendMessage(chatId, "❌ Tavsif juda qisqa! Kamida 10 ta belgi kiriting.");
                    return;
                }
                session.setDescription(text.trim());
                sendToChannelForGrading(session);
                completeSubmission(chatId);
                break;

            default:
                sendMessage(chatId, "❌ Noto'g'ri buyruq. /start bilan qaytadan boshlang.");
        }
    }


    private void showClassSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📚 <b>Sinfingizni tanlang:</b>");
        message.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        String[][] classes = {
                {"5-A", "5-B", "5-C"},
                {"6-A", "6-B", "6-C"},
                {"7-A", "7-B", "7-C"},
                {"8-A", "8-B", "8-C"},
                {"9-A", "9-B", "9-C"},
                {"10-A", "10-B", "10-C"},
                {"11-A", "11-B", "11-C"}
        };

        for (String[] row : classes) {
            List<InlineKeyboardButton> keyboardRow = new ArrayList<>();
            for (String className : row) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(className);
                button.setCallbackData("class_" + className);
                keyboardRow.add(button);
            }
            keyboard.add(keyboardRow);
        }

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        sendMessage(message);
    }


    private void handleCallbackQuery(Update update) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String data = update.getCallbackQuery().getData();

        if (data.startsWith("class_")) {
            handleClassSelection(chatId, messageId, data);
        } else if (data.equals("homework") || data.equals("control")) {
            handleWorkTypeSelection(chatId, messageId, data);
        } else if (data.startsWith("grade_")) {
            handleGrading(chatId, messageId, data);
        }



    }


    private void handleClassSelection(Long chatId, Integer messageId, String data) {
        String className = data.substring(6);
        UserSession session = userSessions.get(chatId);

        if (session != null) {
            session.setClassName(className);
            session.setState(SessionState.SELECTING_WORK_TYPE);
            showWorkTypeSelection(chatId, messageId);
        }
    }


    private void showWorkTypeSelection(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("📝 <b>Vazifa turini tanlang:</b>");
        message.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton homeworkBtn = new InlineKeyboardButton();
        homeworkBtn.setText("📖 Uy vazifasi");
        homeworkBtn.setCallbackData("homework");
        row.add(homeworkBtn);

        InlineKeyboardButton controlBtn = new InlineKeyboardButton();
        controlBtn.setText("📋 Nazorat ishi");
        controlBtn.setCallbackData("control");
        row.add(controlBtn);

        keyboard.add(row);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleWorkTypeSelection(Long chatId, Integer messageId, String data) {
        UserSession session = userSessions.get(chatId);

        if (session != null) {
            session.setWorkType(data.equals("homework") ? "Uy vazifasi" : "Nazorat ishi");
            session.setState(SessionState.WAITING_DESCRIPTION);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(
                    "✍️ <b>Vazifa tavsifini kiriting:</b>\n\n" +
                            "📌 Nimani bajardingiz?\n" +
                            "📌 Qaysi mavzuda?\n" +
                            "📌 Qo'shimcha izohlar...\n\n" +
                            "<i>Kamida 10 ta belgi yozing.</i>"
            );
            message.setParseMode("HTML");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }


    private void sendToChannelForGrading(UserSession session) {
        int submissionId = DatabaseManager.saveSubmission(session);

        if (submissionId == -1) {
            sendMessage(session.getChatId(), "❌ Xatolik yuz berdi. Qaytadan urinib ko'ring.");
            return;
        }

        String channelText = String.format(
                "🆕 <b>YANGI VAZIFA TOPSHIRILDI</b>\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "📚 <b>Turi:</b> %s\n" +
                        "👤 <b>O'quvchi:</b> %s\n" +
                        "🎓 <b>Sinf:</b> %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "📝 <b>Tavsif:</b>\n%s\n\n" +
                        "⏰ <b>Sana:</b> %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "👨‍🏫 <b>Ustoz, iltimos baholang:</b>",
                session.getWorkType(),
                session.getStudentName(),
                session.getClassName(),
                session.getDescription(),
                getCurrentDateTime()
        );

        SendMessage message = new SendMessage();
        message.setChatId(CHANNEL_ID);
        message.setText(channelText);
        message.setParseMode("HTML");
        message.setReplyMarkup(createGradingKeyboard(submissionId));

        sendMessage(message);
    }


    private InlineKeyboardMarkup createGradingKeyboard(int submissionId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();


        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (int grade = 100; grade >= 85; grade -= 5) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(grade + "%");
            btn.setCallbackData("grade_" + submissionId + "_" + grade);
            row1.add(btn);
        }
        keyboard.add(row1);


        List<InlineKeyboardButton> row2 = new ArrayList<>();
        for (int grade = 80; grade >= 65; grade -= 5) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(grade + "%");
            btn.setCallbackData("grade_" + submissionId + "_" + grade);
            row2.add(btn);
        }
        keyboard.add(row2);


        List<InlineKeyboardButton> row3 = new ArrayList<>();
        for (int grade = 60; grade >= 50; grade -= 5) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(grade + "%");
            btn.setCallbackData("grade_" + submissionId + "_" + grade);
            row3.add(btn);
        }
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }


    private void handleGrading(Long chatId, Integer messageId, String data) {
        String[] parts = data.split("_");
        int submissionId = Integer.parseInt(parts[1]);
        int grade = Integer.parseInt(parts[2]);

        SubmissionInfo info = DatabaseManager.updateGrade(submissionId, grade);

        if (info != null) {

            updateChannelMessage(chatId, messageId, info, grade);


            sendGradeToStudent(info, grade);
        }
    }


    private void updateChannelMessage(Long chatId, Integer messageId, SubmissionInfo info, int grade) {
        String emoji = getGradeEmoji(grade);
        String gradeLevel = getGradeLevel(grade);

        String updatedText = String.format(
                "✅ <b>BAHOLANDI</b>\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "📚 <b>Turi:</b> %s\n" +
                        "👤 <b>O'quvchi:</b> %s\n" +
                        "🎓 <b>Sinf:</b> %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "📝 <b>Tavsif:</b>\n%s\n\n" +
                        "⏰ <b>Topshirilgan:</b> %s\n" +
                        "✅ <b>Baholangan:</b> %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "%s <b>BAHO: %d%%</b> (%s)",
                info.workType, info.studentName, info.className,
                info.description, info.submissionDate,
                getCurrentDateTime(), emoji, grade, gradeLevel
        );

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(updatedText);
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendGradeToStudent(SubmissionInfo info, int grade) {
        String emoji = getGradeEmoji(grade);
        String gradeLevel = getGradeLevel(grade);

        String resultText = String.format(
                "%s <b>VAZIFANGIZ BAHOLANDI!</b>\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "📚 <b>Vazifa:</b> %s\n" +
                        "🎓 <b>Sinf:</b> %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "🎯 <b>Sizning bahoyingiz:</b>\n" +
                        "%s <b>%d%%</b> - %s\n\n" +
                        "📊 <b>Baholash tizimi:</b>\n" +
                        "• 90-100%% = A'lo\n" +
                        "• 70-89%% = Yaxshi\n" +
                        "• 50-69%% = Qoniqarli\n" +
                        "• 50%% dan kam = Qoniqarsiz\n\n" +
                        "💪 Yangi vazifa topshirish uchun /start",
                emoji, info.workType, info.className,
                emoji, grade, gradeLevel
        );

        sendHtmlMessage(info.userChatId, resultText);
    }


    private void completeSubmission(Long chatId) {
        userSessions.remove(chatId);

        String successText =
                "✅ <b>MUVAFFAQIYATLI!</b>\n\n" +
                        "Vazifangiz kanalga yuborildi va baholash uchun ustoz oldiga keldi.\n\n" +
                        "📊 Natija tez orada sizga xabar qilinadi!\n\n" +
                        "🔄 Yangi vazifa topshirish: /start\n" +
                        "📈 Statistika: /stats\n" +
                        "❓ Yordam: /help";

        sendHtmlMessage(chatId, successText);
    }


    private void sendHelpMessage(Long chatId) {
        String helpText =
                "📚 <b>BOT QANDAY ISHLAYDI?</b>\n\n" +
                        "<b>1️⃣ Boshlash:</b>\n" +
                        "/start - Botni ishga tushirish\n\n" +
                        "<b>2️⃣ Ma'lumot kiriting:</b>\n" +
                        "• Ismingiz\n" +
                        "• Sinfingiz (5-11)\n" +
                        "• Vazifa turi\n" +
                        "• Tavsif\n\n" +
                        "<b>3️⃣ Baholash:</b>\n" +
                        "Ustoz vazifangizni ko'rib chiqadi va 50-100% orasida baho beradi.\n\n" +
                        "<b>4️⃣ Natija:</b>\n" +
                        "Siz avtomatik ravishda bahoyingizni olasiz!\n\n" +
                        "<b>📊 Buyruqlar:</b>\n" +
                        "/start - Yangi vazifa topshirish\n" +
                        "/stats - Sizning statistikangiz\n" +
                        "/help - Yordam\n\n" +
                        "❓ Savol bo'lsa, ustoz bilan bog'laning!";

        sendHtmlMessage(chatId, helpText);
    }


    private void sendUserStatistics(Long chatId) {
        List<SubmissionInfo> submissions = DatabaseManager.getUserSubmissions(chatId);

        if (submissions.isEmpty()) {
            sendMessage(chatId, "📊 Sizda hali topshirilgan vazifalar yo'q.\n\n/start buyrug'i bilan boshlang!");
            return;
        }

        int total = submissions.size();
        int graded = 0;
        double totalGrade = 0;

        for (SubmissionInfo sub : submissions) {
            if (sub.grade != null) {
                graded++;
                totalGrade += sub.grade;
            }
        }

        double avgGrade = graded > 0 ? totalGrade / graded : 0;

        String statsText = String.format(
                "📊 <b>SIZNING STATISTIKANGIZ</b>\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "📚 <b>Jami topshirgan:</b> %d ta\n" +
                        "✅ <b>Baholangan:</b> %d ta\n" +
                        "⏳ <b>Kutilmoqda:</b> %d ta\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "📈 <b>O'rtacha ball:</b> %.1f%%\n\n" +
                        "💡 Yangi vazifa: /start",
                total, graded, total - graded, avgGrade
        );

        sendHtmlMessage(chatId, statsText);
    }


    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        sendMessage(message);
    }

    private void sendHtmlMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        sendMessage(message);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private String getGradeEmoji(int grade) {
        if (grade >= 90) return "🏆";
        if (grade >= 70) return "⭐";
        if (grade >= 50) return "✅";
        return "❌";
    }

    private String getGradeLevel(int grade) {
        if (grade >= 90) return "A'lo";
        if (grade >= 70) return "Yaxshi";
        if (grade >= 50) return "Qoniqarli";
        return "Qoniqarsiz";
    }
}


class UserSession {
    private Long chatId;
    private SessionState state;
    private String studentName;
    private String className;
    private String workType;
    private String description;

    public UserSession(Long chatId) {
        this.chatId = chatId;
        this.state = SessionState.WAITING_NAME;
    }


    public Long getChatId() { return chatId; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}


enum SessionState {
    WAITING_NAME,
    SELECTING_CLASS,
    SELECTING_WORK_TYPE,
    WAITING_DESCRIPTION
}


class SubmissionInfo {
    public int id;
    public String studentName;
    public String className;
    public String workType;
    public String description;
    public String submissionDate;
    public Integer grade;
    public Long userChatId;

    public SubmissionInfo(int id, String studentName, String className,
                          String workType, String description, String submissionDate,
                          Integer grade, Long userChatId) {
        this.id = id;
        this.studentName = studentName;
        this.className = className;
        this.workType = workType;
        this.description = description;
        this.submissionDate = submissionDate;
        this.grade = grade;
        this.userChatId = userChatId;
    }
}


class DatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/homework_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "root";

    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String createTableSQL =
                    "CREATE TABLE IF NOT EXISTS submissions (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_name VARCHAR(255) NOT NULL, " +
                            "class_name VARCHAR(50) NOT NULL, " +
                            "work_type VARCHAR(50) NOT NULL, " +
                            "description TEXT NOT NULL, " +
                            "submission_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "grade INTEGER, " +
                            "graded_date TIMESTAMP, " +
                            "user_chat_id BIGINT NOT NULL)";

            Statement stmt = conn.createStatement();
            stmt.execute(createTableSQL);
            System.out.println("✅ Database tayyor!");
        } catch (SQLException e) {
            System.err.println("❌ Database xatosi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static int saveSubmission(UserSession session) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "INSERT INTO submissions (student_name, class_name, work_type, description, user_chat_id) " +
                    "VALUES (?, ?, ?, ?, ?) RETURNING id";

            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, session.getStudentName());
            pstmt.setString(2, session.getClassName());
            pstmt.setString(3, session.getWorkType());
            pstmt.setString(4, session.getDescription());
            pstmt.setLong(5, session.getChatId());

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static SubmissionInfo updateGrade(int submissionId, int grade) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "UPDATE submissions SET grade = ?, graded_date = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, grade);
            pstmt.setInt(2, submissionId);
            pstmt.executeUpdate();

            sql = "SELECT * FROM submissions WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, submissionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new SubmissionInfo(
                        rs.getInt("id"),
                        rs.getString("student_name"),
                        rs.getString("class_name"),
                        rs.getString("work_type"),
                        rs.getString("description"),
                        rs.getTimestamp("submission_date").toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                        rs.getInt("grade"),
                        rs.getLong("user_chat_id")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<SubmissionInfo> getUserSubmissions(Long chatId) {
        List<SubmissionInfo> submissions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT * FROM submissions WHERE user_chat_id = ? ORDER BY submission_date DESC";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Integer grade = rs.getObject("grade") != null ? rs.getInt("grade") : null;

                submissions.add(new SubmissionInfo(
                        rs.getInt("id"),
                        rs.getString("student_name"),
                        rs.getString("class_name"),
                        rs.getString("work_type"),
                        rs.getString("description"),
                        rs.getTimestamp("submission_date").toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                        grade,
                        rs.getLong("user_chat_id")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return submissions;
    }
}