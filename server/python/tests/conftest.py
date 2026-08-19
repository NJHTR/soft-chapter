import os
import sys

SERVER_PYTHON = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if SERVER_PYTHON not in sys.path:
    sys.path.insert(0, SERVER_PYTHON)
