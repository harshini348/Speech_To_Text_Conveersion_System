package com.example.speechtotext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.vosk.Model;
import org.vosk.Recognizer;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends JFrame {
    private final JTextField name = new JTextField(25), email = new JTextField(25), phone = new JTextField(25);
    private final JTextArea transcript = new JTextArea(9, 48);
    private final JLabel status = new JLabel("Ready. Register a user first.");
    private final JButton start = new JButton("Start Recording"), stop = new JButton("Stop Recording"), save = new JButton("Save Text"), history = new JButton("View History");
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final ObjectMapper json = new ObjectMapper();
    private Connection db;
    private int userId = -1;
    private TargetDataLine microphone;

    public App() throws Exception {
        super("Speech-to-Text Conversion System");
        db = DriverManager.getConnection("jdbc:sqlite:speech_history.db");
        try (Statement s = db.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT, phone TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS conversions (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, text TEXT NOT NULL, created_at TEXT NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id))");
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel fields = new JPanel(new GridLayout(4, 2, 8, 8));
        fields.setBorder(BorderFactory.createTitledBorder("User Details"));
        fields.add(new JLabel("Name:")); fields.add(name);
        fields.add(new JLabel("Email:")); fields.add(email);
        fields.add(new JLabel("Phone:")); fields.add(phone);
        JButton register = new JButton("Register User");
        fields.add(register); fields.add(new JLabel("Register before recording"));
        JPanel controls = new JPanel();
        controls.add(start); controls.add(stop); controls.add(save); controls.add(history);
        stop.setEnabled(false);
        transcript.setLineWrap(true); transcript.setWrapStyleWord(true);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createTitledBorder("Converted Text (editable)"));
        center.add(new JScrollPane(transcript), BorderLayout.CENTER);
        center.add(controls, BorderLayout.SOUTH);
        add(fields, BorderLayout.NORTH); add(center, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        register.addActionListener(e -> registerUser());
        start.addActionListener(e -> startRecording());
        stop.addActionListener(e -> stopRecording());
        save.addActionListener(e -> saveText());
        history.addActionListener(e -> showHistory());
        pack(); setLocationRelativeTo(null);
    }
    private void registerUser() {
        if (name.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a name."); return; }
        try (PreparedStatement p = db.prepareStatement("INSERT INTO users(name,email,phone) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,name.getText().trim()); p.setString(2,email.getText().trim()); p.setString(3,phone.getText().trim()); p.executeUpdate();
            try (ResultSet r=p.getGeneratedKeys()) { if(r.next()) userId=r.getInt(1); }
            status.setText("Registered user ID: " + userId);
        } catch (SQLException ex) { error(ex); }
    }
    private void startRecording() {
        if (userId < 0) { JOptionPane.showMessageDialog(this,"Register a user first."); return; }
        Path modelPath = Path.of("model", "vosk-model-small-en-us-0.15");
        if (!Files.isDirectory(modelPath)) { JOptionPane.showMessageDialog(this,"Download and extract the Vosk model to:\n" + modelPath.toAbsolutePath()); return; }
        recording.set(true); start.setEnabled(false); stop.setEnabled(true); status.setText("Loading model / microphone...");
        new Thread(() -> {
            AudioFormat format = new AudioFormat(16000f,16,1,true,false);
            try (Model model = new Model(modelPath.toString()); Recognizer recognizer = new Recognizer(model,16000)) {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class,format);
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format); microphone.start();
                SwingUtilities.invokeLater(() -> status.setText("Listening... speak clearly, then press Stop."));
                byte[] buffer = new byte[4096];
                while(recording.get()) {
                    int count = microphone.read(buffer,0,buffer.length);
                    if(count > 0 && recognizer.acceptWaveForm(buffer,count)) appendRecognized(recognizer.getResult());
                }
                appendRecognized(recognizer.getFinalResult());
            } catch(Exception ex) { SwingUtilities.invokeLater(() -> error(ex)); }
            finally {
                if(microphone != null) { microphone.stop(); microphone.close(); microphone=null; }
                recording.set(false);
                SwingUtilities.invokeLater(() -> {start.setEnabled(true);stop.setEnabled(false);status.setText("Recording stopped. Review and save your text.");});
            }
        },"Speech-recognition").start();
    }
    private void appendRecognized(String result) {
        try {
            String words=json.readTree(result).path("text").asText("").trim();
            if(!words.isEmpty()) SwingUtilities.invokeLater(() -> transcript.append((transcript.getText().isBlank()?"":" ")+words));
        } catch(Exception ignored) { }
    }
    private void stopRecording() { recording.set(false); stop.setEnabled(false); status.setText("Finishing recognition..."); }
    private void saveText() {
        if(userId<0) {JOptionPane.showMessageDialog(this,"Register first.");return;}
        String words=transcript.getText().trim();
        if(words.isEmpty()) {JOptionPane.showMessageDialog(this,"No text to save.");return;}
        try(PreparedStatement p=db.prepareStatement("INSERT INTO conversions(user_id,text,created_at) VALUES(?,?,?)")) {
            p.setInt(1,userId);p.setString(2,words);p.setString(3,LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));p.executeUpdate();
            status.setText("Conversion saved to database."); JOptionPane.showMessageDialog(this,"Saved successfully!");
        } catch(SQLException ex) {error(ex);}
    }
    private void showHistory() {
        if(userId<0) {JOptionPane.showMessageDialog(this,"Register first.");return;}
        StringBuilder out=new StringBuilder();
        try(PreparedStatement p=db.prepareStatement("SELECT created_at,text FROM conversions WHERE user_id=? ORDER BY id DESC")) {
            p.setInt(1,userId);
            try(ResultSet r=p.executeQuery()) {while(r.next())out.append(r.getString(1)).append("\n").append(r.getString(2)).append("\n\n");}
            JTextArea area=new JTextArea(out.length()==0?"No history yet.":out.toString(),18,50);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this,new JScrollPane(area),"Conversion History",JOptionPane.INFORMATION_MESSAGE);
        } catch(SQLException ex) {error(ex);}
    }
    private void error(Exception ex) {JOptionPane.showMessageDialog(this,ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);}
    public static void main(String[] args) {SwingUtilities.invokeLater(() -> {try {new App().setVisible(true);} catch(Exception e) {JOptionPane.showMessageDialog(null,e.toString());}});}
}
