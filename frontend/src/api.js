import { createApiClient } from './api-client.js'
import { mock, resetDemo } from './demo-api.js'

// Demo data is opt-in; connection errors never switch the data source.
const demo = import.meta.env.VITE_DEMO_MODE === 'true'
export const api = demo ? mock : createApiClient({ baseUrl: import.meta.env.VITE_API_URL || '/api' })
export { resetDemo }
