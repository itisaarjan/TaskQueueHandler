import React, { useState, useRef } from 'react';
import axios from 'axios';
import './App.css';

const LAMBDA_DOWNLOAD_URL = 'https://exljdo7xv3.execute-api.us-east-1.amazonaws.com/download';
const TASKDB_URL = 'http://localhost:8002';

function App() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [options, setOptions] = useState({
    grayscale: false,
    resize: false,
    blur: false,
    invert: false,
    watermark: false
  });
  const [watermarkText, setWatermarkText] = useState('');
  const [uploading, setUploading] = useState(false);
  const [taskId, setTaskId] = useState(null);
  const [status, setStatus] = useState('');
  const [processedImageUrl, setProcessedImageUrl] = useState(null);
  const [error, setError] = useState(null);
  const fileInputRef = useRef(null);

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file && file.type.startsWith('image/')) {
      setSelectedFile(file);
      setError(null);
      
      // Create preview
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreview(reader.result);
      };
      reader.readAsDataURL(file);
    } else {
      setError('Please select a valid image file');
    }
  };

  const handleOptionChange = (option) => {
    setOptions(prev => ({
      ...prev,
      [option]: !prev[option]
    }));
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setError('Please select an image first');
      return;
    }

    setUploading(true);
    setError(null);
    setProcessedImageUrl(null);
    setStatus('Uploading...');

    try {
      const formData = new FormData();
      formData.append('type', 'image');
      formData.append('content', selectedFile);
      
      if (options.grayscale) formData.append('grayscale', 'true');
      if (options.resize) formData.append('resize', 'true');
      if (options.blur) formData.append('blur', 'true');
      if (options.invert) formData.append('invert', 'true');
      if (options.watermark && watermarkText) formData.append('watermark', watermarkText);

      const response = await axios.post('/tasks/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });

      const { id } = response.data;
      setTaskId(id);
      setStatus('Processing...');
      
      // Start polling for task status
      pollTaskStatus(id);
    } catch (err) {
      setError('Upload failed: ' + (err.response?.data?.message || err.message));
      setUploading(false);
    }
  };

  const pollTaskStatus = async (id) => {
    let attempts = 0;
    const maxAttempts = 60; // 60 attempts with 2s interval = 2 minutes max
    
    const poll = setInterval(async () => {
      attempts++;
      
      try {
        const response = await axios.get(`${TASKDB_URL}/tasks/${id}`);
        const task = response.data;
        
        if (task.status === 'completed') {
          clearInterval(poll);
          setStatus('Completed!');
          setUploading(false);
          
          // Download the processed image via Lambda
          if (task.resultUrl) {
            const encodedKey = encodeURIComponent(task.resultUrl);
            const downloadUrl = `${LAMBDA_DOWNLOAD_URL}?key=${encodedKey}`;
            setProcessedImageUrl(downloadUrl);
          }
        } else if (task.status === 'failed') {
          clearInterval(poll);
          setError('Processing failed: ' + (task.error || 'Unknown error'));
          setUploading(false);
        } else {
          setStatus(`Processing... (${task.status})`);
        }
      } catch (err) {
        if (attempts >= maxAttempts) {
          clearInterval(poll);
          setError('Timeout: Task did not complete within 2 minutes');
          setUploading(false);
        }
      }
    }, 2000); // Poll every 2 seconds
  };

  const handleReset = () => {
    setSelectedFile(null);
    setPreview(null);
    setOptions({
      grayscale: false,
      resize: false,
      blur: false,
      invert: false,
      watermark: false
    });
    setWatermarkText('');
    setTaskId(null);
    setStatus('');
    setProcessedImageUrl(null);
    setError(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <div className="App">
      <div className="container">
        <header className="header">
          <h1>🎨 Image Processor</h1>
          <p>Upload and process your images with various effects</p>
        </header>

        <div className="main-content">
          {/* Upload Section */}
          <div className="upload-section">
            <div className="file-input-wrapper">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileSelect}
                id="file-input"
                className="file-input"
              />
              <label htmlFor="file-input" className="file-input-label">
                {selectedFile ? selectedFile.name : '📁 Choose Image'}
              </label>
            </div>

            {preview && (
              <div className="preview-section">
                <h3>Original Image</h3>
                <img src={preview} alt="Preview" className="preview-image" />
              </div>
            )}
          </div>

          {/* Options Section */}
          {selectedFile && (
            <div className="options-section">
              <h3>Processing Options</h3>
              <div className="options-grid">
                <label className="option-item">
                  <input
                    type="checkbox"
                    checked={options.grayscale}
                    onChange={() => handleOptionChange('grayscale')}
                  />
                  <span>🌑 Grayscale</span>
                </label>

                <label className="option-item">
                  <input
                    type="checkbox"
                    checked={options.resize}
                    onChange={() => handleOptionChange('resize')}
                  />
                  <span>📏 Resize (max 1024px)</span>
                </label>

                <label className="option-item">
                  <input
                    type="checkbox"
                    checked={options.blur}
                    onChange={() => handleOptionChange('blur')}
                  />
                  <span>🌫️ Blur</span>
                </label>

                <label className="option-item">
                  <input
                    type="checkbox"
                    checked={options.invert}
                    onChange={() => handleOptionChange('invert')}
                  />
                  <span>🔄 Invert Colors</span>
                </label>

                <label className="option-item watermark-option">
                  <input
                    type="checkbox"
                    checked={options.watermark}
                    onChange={() => handleOptionChange('watermark')}
                  />
                  <span>💧 Watermark</span>
                  {options.watermark && (
                    <input
                      type="text"
                      placeholder="Watermark text"
                      value={watermarkText}
                      onChange={(e) => setWatermarkText(e.target.value)}
                      className="watermark-input"
                    />
                  )}
                </label>
              </div>
            </div>
          )}

          {/* Action Buttons */}
          {selectedFile && (
            <div className="action-buttons">
              <button 
                onClick={handleUpload} 
                disabled={uploading}
                className="btn btn-primary"
              >
                {uploading ? '⏳ Processing...' : '🚀 Process Image'}
              </button>
              <button 
                onClick={handleReset}
                className="btn btn-secondary"
              >
                🔄 Reset
              </button>
            </div>
          )}

          {/* Status */}
          {status && (
            <div className="status-section">
              <div className={`status-message ${uploading ? 'processing' : 'completed'}`}>
                {status}
              </div>
              {taskId && (
                <div className="task-id">Task ID: {taskId}</div>
              )}
            </div>
          )}

          {/* Error */}
          {error && (
            <div className="error-section">
              ❌ {error}
            </div>
          )}

          {/* Processed Image */}
          {processedImageUrl && (
            <div className="result-section">
              <h3>✨ Processed Image</h3>
              <img 
                src={processedImageUrl} 
                alt="Processed" 
                className="processed-image"
              />
              <a 
                href={processedImageUrl} 
                download="processed-image.jpg"
                className="btn btn-download"
              >
                ⬇️ Download Image
              </a>
            </div>
          )}
        </div>

        <footer className="footer">
          <p>Powered by AWS Lambda, S3, and Spring Boot Microservices</p>
        </footer>
      </div>
    </div>
  );
}

export default App;

