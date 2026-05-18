18/5
hiểu cách sử dụng test, với mục đích kiểm tra token của 2 services là auth và patient


localstack cho phép  phát triển xây dựng và kiểm thử các ứng dụng đám mây mà không cần kết nối trực tiếp đến AWS, giúp tiết kiệm chi phí, tăng tốc độ phát triển và làm việc offline dễ dàng
(nói ngắn gọn là 1 trình duyệt giúp testing bằng AWS FREE)

ecs service giống như 1 service cha bảo vệ các service con với internet bên ngoài đồng thời cũng backup cho các service nhỏ hơn, nếu các service con như auth, patient mà ngưng vì 1 lý do nào đó thì ecs task ngưng theo nhưng ecs service thì khác, service này sẽ không ngưng và đồng service cha sẽ khởi động lại các service con
tại sao lại dùng ecs thay vì các ứng dụng deployment khác tiện hơn, nhanh hơn và bớt cầu kì hơn
lý do:
-nếu sử dụng các deployment kia thì chỉ hợp ới các dự án full-stack mini chỉ cần vài click là xong còn để cho các project phức tạp hơn thì không ổn
-có thể cho nhiều người sử dụng tránh overlap




