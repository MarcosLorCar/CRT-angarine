import express from 'express';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3001;

// Resolve paths for ES Modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendDistPath = path.resolve(__dirname, '../../dist');

app.use(cors());
app.use(express.json());

// API routes
app.get('/api/hello', (req, res) => {
  res.json({ message: 'Hello from the backend Express server!' });
});

// Serve frontend static assets in production
if (process.env.NODE_ENV === 'production') {
  app.use(express.static(frontendDistPath));

  // Catch-all route to serve index.html for SPA routing
  app.get('*', (req, res) => {
    res.sendFile(path.join(frontendDistPath, 'index.html'));
  });
} else {
  // Helpful route in development
  app.get('/', (req, res) => {
    res.send('Backend API is running. Switch to frontend on port 5173 for client app.');
  });
}

app.listen(PORT, () => {
  console.log(`[server]: Server is running at http://localhost:${PORT}`);
  if (process.env.NODE_ENV === 'production') {
    console.log(`[server]: Serving static assets from ${frontendDistPath}`);
  }
});
