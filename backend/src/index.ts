import 'dotenv/config';
import express from 'express';
import morgan from 'morgan';
import cors from 'cors';
import { z } from 'zod';

const app = express();
app.use(cors());
app.use(express.json());
app.use(morgan('dev'));

const RegisterSchema = z.object({
  email: z.string().email(),
  phone_e164: z.string().min(8),
  password: z.string().min(6).optional()
});

app.post('/register', (req, res) => {
  const parsed = RegisterSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });
  // TODO: create user, persist userId/email/phone_e164 (no phrases/buddies yet)
  return res.status(202).json({ ok: true, userId: 'stub-user-id' });
});

const EmailSchema = z.object({
  email: z.string().email(),
  qrPath: z.string().default(process.env.WYPE_DO_QR_PATH || 'templates/assets/wype_do_qr.png')
});

app.post('/send-do-email', async (req, res) => {
  const parsed = EmailSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });
  // TODO: send email using templates/do_provisioning_email.(html|txt) and attach QR
  // TODO: record timestamp + optional device token issuance
  return res.status(202).json({ ok: true });
});

app.post('/otp/start', (req, res) => {
  // TODO: start phone/email OTP for re-identification after DO
  return res.status(202).json({ ok: true });
});

app.post('/otp/verify', (req, res) => {
  // TODO: verify OTP and look up userId by verified phone/email
  return res.status(200).json({ ok: true, userId: 'stub-user-id' });
});

const port = process.env.PORT ? Number(process.env.PORT) : 8080;
app.listen(port, () => {
  console.log('Wype backend stub listening on port', port);
});
