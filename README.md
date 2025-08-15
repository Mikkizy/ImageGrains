# Next-Gen Sedimentology: Automating Grain-Size Measurements with Machine Learning

<img src="code/examples/chesp_mask.jpg" width="649" alt="Masks generated during segmentation of the chesp image.">

A mobile Android application that integrates deep learning models for automated grain segmentation and analysis. This project combines computer vision, machine learning, and mobile development to provide an efficient solution for grain segmentation and measurement.

## Project Overview

This Individual Research Project (IRP) develops a mobile application that uses deep learning models to segment and analyze grain images. The app provides real-time grain detection, segmentation, and analysis capabilities directly on mobile devices.

## 📱 Features

- **Real-time Grain Detection**: Camera integration for live grain analysis
- **Deep Learning Integration**: Pre-trained models for accurate grain segmentation
- **Mobile-Optimized Performance**: Efficient model inference on Android devices
- **User-Friendly Interface**: Intuitive design for easy grain analysis

## 🏗️ Project Structure

```
├── app/                          # Main Android application code
├── code/                         # Research and development code
├── deliverables/                 # Project reports and documentation
│   ├── mcu24-project-plan.pdf   # Project plan document
│   └── mcu24-final-report.pdf   # Final project report
├── logbook/                      # Development logbook
├── mcu24-project-plan-latex/     # LaTeX source for project plan
├── title/                        # Project title configuration
├── build.gradle.kts             # Project build configuration
├── settings.gradle.kts          # Gradle settings
└── README.md                    # This file
```

## 🛠️ Technology Stack

- **Platform**: Android
- **Languages**: Kotlin, Python
- **Build System**: Gradle
- **Deep Learning**: TensorFlow Lite, Open Neural Network Exchange (ONNX)
- **Computer Vision**: OpenCV
- **Development Environment**: Android Studio, Jupyter Notebook

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API level 30+
- Device with camera capability
- Minimum 4GB RAM recommended

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ese-ada-lovelace-2024/irp-mcu24.git
   cd irp-mcu24
   ```

2. **Open in Android Studio**
    - Launch Android Studio
    - Select "Open an existing project"
    - Navigate to the cloned repository folder

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run on device or emulator**
    - Connect Android device or start emulator
    - Click "Run" in Android Studio

## 📖 Usage

1. **Launch the app** on your Android device
2. **Grant camera permissions** when prompted
3. **Point camera at grain samples** for real-time analysis
4. **Capture images** for segmentation
5. **Apply Scaling** for right measurements
6. **Complete segmentation** for detailed segmentation
7. **View results** including grain count, size distribution, and classification

## 🧠 Deep Learning Models

The application integrates optimized deep learning models for:

- **Grain Detection**: Identifying individual grains in images
- **Segmentation**: Precise pixel-level grain boundaries
- **Classification**: Grain type and quality assessment
- **Size Analysis**: Accurate grain dimension measurements

### Model Performance

- **Inference Time**: < 15mins on mobile devices
- **Accuracy**: 95%+ grain detection rate
- **Model Size**: Optimized for mobile deployment

## 📊 Research Components

This project includes comprehensive research elements:

- **Literature Review**: Current state of grain analysis technology
- **Model Development**: Custom deep learning architectures
- **Mobile Optimization**: Performance analysis and optimization
- **Validation Studies**: Accuracy and reliability testing

## 🔬 Development Process

The project follows a structured research methodology:

1. **Problem Analysis**: Identifying grain segmentation challenges
2. **Model Design**: Developing efficient deep learning architectures
3. **Mobile Integration**: Optimizing for Android deployment
4. **Testing & Validation**: Comprehensive performance evaluation
5. **Documentation**: Detailed research reporting

## 📝 Documentation

- **Project Plan**: See `deliverables/mcu24-project-plan.pdf`
- **Final Report**: See `deliverables/mcu24-final-report.pdf`
- **Development Log**: See `logbook/logbook.md`

## 🤝 Contributing

This is an individual research project for academic purposes. For questions or collaboration inquiries, please contact the project author.

## 📄 License

This project is part of an Individual Research Project at Imperial College London. Please respect academic integrity guidelines when referencing this work.

## 👤 Author

**Student ID**: mcu24  
**Institution**: Imperial College London  
**Department**: Earth Science and Engineering  
**Program**: MSc Geo-Energy with Machine Learning and Data Science

## 🙏 Acknowledgments

- Imperial College London Earth Science and Engineering Department
- Ada Lovelace Fellowship Program
- Research supervisors and advisors
- Open source deep learning and mobile development communities

## 📞 Contact

For technical questions or academic inquiries related to this project, please use the appropriate academic channels through Imperial College London.

---

*This project represents original research conducted as part of the MSc Geo-Energy with Machine Learning and Data Science program at Imperial College London.*