package Chatting;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;


public class ChatServer {
    private static final int PORT = 12345;
    private static int roomNumber = 0;
    private static List<String> roomList = new ArrayList<>();
    private static Map<String, PrintWriter> clientWriters = new HashMap<>();
    private static Map<String, Integer> clientRooms = new HashMap<>();
    private static final String CHAT_LOG_FILE = "chatlog.txt";

    public static void main(String[] args) {
        try(ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("채팅 서버 시작~!");
            while(true){
                Socket clientSocket = serverSocket.accept();
                System.out.println("새로운 참가자가 입장하셨습니다.");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String nickname;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try{
                writer = new PrintWriter(clientSocket.getOutputStream(),true);
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                while (true) {
                    nickname = reader.readLine();
                    if (nickname == null) {
                        return;
                    }
                    synchronized (clientWriters) {
                        if (!clientWriters.containsKey(nickname)) {
                            clientWriters.put(nickname, writer);
                            break;
                        } else {
                            writer.println("이미 존재하는 닉네임입니다. 다른 닉네임을 입력해주세요!");
                        }
                    }
                }
                System.out.println("새로운 참가자가 입장하셨습니다 : " + nickname);

                writer.println("채팅에 오신 것을 환영합니다, " + nickname + "님!");

                synchronized ((clientWriters)) {
                    clientWriters.put(nickname, writer);
                }

                broadcastMessage(nickname + "님이 연결했습니다.");

                sendCommandList();

                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    if (inputLine.startsWith("/")) {
                        handleCommand(inputLine);
                    } else {
                        broadcastMessage(nickname + ": " + inputLine);
                        saveChat(nickname,inputLine); //채팅내용을 로그파일에 저장
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally{
                try{
                    synchronized ((clientWriters)){
                        clientWriters.remove(nickname);
                    }
                    broadcastMessage(nickname+"님이 채팅을 남겼습니다.");
                    reader.close();
                    writer.close();
                    clientSocket.close();
                }catch (IOException e){
                    System.err.println("에러가 발생하였습니다: "+e.getMessage());
                }
            }
        }

        private void broadcastMessage(String message) {
            synchronized ((clientWriters)){
                if(clientRooms.containsKey(nickname)){
                    int clientRoom = clientRooms.get(nickname); // 클라이언트가 속한 방 번호 가져오기
                    for (Map.Entry<String, Integer> entry : clientRooms.entrySet()) {
                        if (entry.getValue() == clientRoom) { // 해당 방에 속한 클라이언트에게만 메시지 전송
                            String client = entry.getKey();
                            PrintWriter writer = clientWriters.get(client);
                            writer.println(message);
                        }
                    }
                }else{
                    for(PrintWriter writer : clientWriters.values()){
                        writer.println(message);
                    }
                }
            }
        }

        private void handleCommand(String command) {
            if(command.equals("/list")){
                sendRoomList();
            }else if(command.equals("/create")){
                createRoom();
            } else if(command.startsWith("/join")){
                joinRoom(command);
            } else if(command.equals("/users")){
                showALlUsers();
            } else if(command.equals("/roomusers")){
                showRoomUsers();
            } else if (command.equals("/exit")){
                exitRoom();
            }else if(command.equals("/bye")){
                try{
                    reader.close();
                    writer.close();
                    clientSocket.close();
                    System.exit(1);
                }catch (IOException e){
                    System.err.println("오류가 발생하였습니다 "+e.getMessage());
                }
            }

        }

        private void sendRoomList() {
            StringBuilder rooms= new StringBuilder();
            if(roomList.isEmpty()){
                rooms.append("해당 방이 현재 존재하지 않습니다.");
            }else{
                for(String room: roomList){
                    rooms.append(room).append("\n");
                }
            }
            writer.println("방 목록:\n"+ rooms.toString());
        }

        private void createRoom() {
            String roomName = (++roomNumber)+" 번";
            roomList.add(roomName);
            writer.println( roomNumber+"번 방이 개설되었습니다!");
        }

        private void joinRoom(String command) {
            String[] numbers = command.split(" ");
            if(numbers.length == 2) {
                int roomNumber = Integer.parseInt(numbers[1]);
                String roomName = (roomNumber) + " 번";
                if (roomList.contains(roomName)) {
                    clientRooms.put(nickname, roomNumber);
                    writer.println(roomName + "에 참가하셨습니다.");
                } else {
                    writer.println("해당 방이 존재하지 않습니다.");
                }
            }else{
                writer.println("명령어가 정확하지 않습니다. 다음 형식으로 다시 입력해주세요. : /join [방번호]");
            }

        }

        private void showALlUsers(){
            writer.println("현재 접속 중인 모든 사용자 목록 : ");
            for (String user : clientWriters.keySet()) {
                writer.println(user);
            }
        }

        private void showRoomUsers() {
            if(clientRooms.containsKey(nickname)){
                int clientRoom = clientRooms.get(nickname);
                StringBuilder usersInRoom= new StringBuilder();
                usersInRoom.append(clientRoom).append("번 방의 사용자 목록\n");
                for(Map.Entry<String, Integer> entry : clientRooms.entrySet()) {
                    if(entry.getValue() == clientRoom){
                        usersInRoom.append(entry.getKey()).append("\n");
                    }
                }
                writer.println(usersInRoom.toString());
            }else{
                writer.println("방에 입장하지 않으셨습니다.");
            }
        }

        private void exitRoom() {
            if (clientRooms.containsKey(nickname)) {
                int roomNumber = clientRooms.get(nickname);
                clientRooms.remove(nickname);
                writer.println("방에서 나오셨습니다");

                broadcastMessage(nickname + "님이 방에서 나가셨습니다.");

                // Check if the room is empty
                if (!clientRooms.containsValue(roomNumber)) {
                    roomList.remove(roomNumber + " 번");
                    broadcastMessage(roomNumber + "번 방이 삭제되었습니다.");
                }
            } else {
                writer.println("아직 방에 입장하지 않으셨습니다.");
            }
        }

        private void saveChat(String nickname, String message){
            try(FileWriter fileWriter = new FileWriter(CHAT_LOG_FILE,true)){
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = simpleDateFormat.format(new Date());
                bufferedWriter.write("["+timestamp+"]"+nickname+": "+message);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }catch(IOException e){
                System.out.println("채팅을 저장하는 중에 오류가 발생하였습니다: "+e.getMessage());
            }
        }

        private void sendCommandList() {
            writer.println("명령어 모음:\n" +
                    "방 목록 보기 : /list\n"+
                    "방 생성 : /create\n"+
                    "방 입장 : /join [방번호]\n"+
                    "방 나가기 : /exit\n"+
                    "현재 접속 중인 모든 사용자의 목록 : /users\n"+
                    "현재 방에 있는 모든 사용자의 목록을 확인할 수 있습니다. : /roomusers\n"+
                    "접속종료 : /bye\n"
            );
        }
    }

}

