/**
 * Client script cho màn hình Check-in Lễ tân (UC-19).
 */
document.addEventListener('DOMContentLoaded', function () {
    // Xử lý Modal chụp ảnh tại quầy
    const photoModal = document.getElementById('photoModal');
    if (!photoModal) return; // Chưa tra cứu hội viên thì không cần xử lý camera

    const cameraVideo = document.getElementById('cameraVideo');
    const cameraCanvas = document.getElementById('cameraCanvas');
    const photoFileInput = document.getElementById('photoFileInput');
    const captureBtn = document.getElementById('captureBtn');
    let videoStream = null;

    function openModal() {
        photoModal.classList.add('open');
        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
            navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 } })//Xin quyền bật Webcam máy tính
                .then(stream => {
                    videoStream = stream;//mở luồng steam
                    cameraVideo.srcObject = stream;
                    cameraVideo.style.display = 'block';//bật màn hình hiển thị webcam
                    cameraCanvas.style.display = 'none';//giấu canvas, vì canvas sẽ hiển thị ảnh chụp từ webcam
                })
                .catch(err => console.warn('Không thể truy cập camera:', err));
        }
    }

    function closeModal() {
        photoModal.classList.remove('open');
        //khi bấm dấu x(data-close-modal), sẽ lamf modal tàng hình trở lại
        if (videoStream) {
            videoStream.getTracks().forEach(track => track.stop());
            videoStream = null;//tắt luồng stream
        }
    }

    // Mở modal khi bấm nút chụp ảnh
    document.querySelectorAll('[data-ci-photo]').forEach(btn => btn.addEventListener('click', openModal));

    // Đóng modal: chỉ đóng khi bấm nút [X] (data-close-modal)
    document.querySelectorAll('[data-close-modal]').forEach(btn => btn.addEventListener('click', closeModal));

    // Bấm nút "Chụp từ webcam"
    if (captureBtn) {
        captureBtn.addEventListener('click', function () {
            if (!videoStream) return;
            const ctx = cameraCanvas.getContext('2d');
            cameraCanvas.width = cameraVideo.videoWidth || 640;
            cameraCanvas.height = cameraVideo.videoHeight || 480;
            ctx.drawImage(cameraVideo, 0, 0, cameraCanvas.width, cameraCanvas.height);

            cameraCanvas.style.display = 'block';//bấm chụp thì sẽ ẩn video hiện canvas
            cameraVideo.style.display = 'none';

            // Chuyển ảnh webcam sang file để submit form
            cameraCanvas.toBlob(blob => {
                if (blob) {
                    const file = new File([blob], 'captured-checkin.jpg', { type: 'image/jpeg' });//đóng gói ảnh canvas thành file captured-checkin.jpg
                    const container = new DataTransfer();
                    container.items.add(file);
                    photoFileInput.files = container.files;
                }
            }, 'image/jpeg', 0.9);
        });
    }
});
