LimbusSkills - Tanglecleaver (Meursault, Limbus Company) cho MMOItems
====================================================================

CÁCH BUILD RA FILE .JAR
1. Cài JDK 17+ và Maven (https://maven.apache.org).
2. Mở terminal trong thư mục này, chạy:
       mvn clean package
3. File plugin nằm ở: target/LimbusSkills-1.0.jar
4. Bỏ vào thư mục plugins/ của server, restart server.

NẾU LỖI KHÔNG TẢI ĐƯỢC MythicLib
- Copy MythicLib.jar từ server vào thư mục libs/ (tạo mới),
  rồi trong pom.xml bỏ khối MythicLib-dist và bỏ comment khối systemPath.

NẾU LỖI BIÊN DỊCH (tên class không tồn tại)
- Tên class của MythicLib thay đổi theo phiên bản
  (SkillHandler, SkillMetadata, SimpleSkillResult). Xem Javadoc/wiki đúng phiên bản của bạn.

DÙNG TRONG GAME
1. /mmoitems edit <TYPE> <ID>  ->  Ability  ->  chọn TANGLECLEAVER
2. Chọn trigger (ví dụ chuột phải) và chỉnh modifier:
     damage-per-coin = 5
     range           = 6
     tremor-damage   = 1.5
     burn-damage     = 1
     reload          = 8
     star-duration   = 10
   Cooldown gợi ý: 8-10 giây.
